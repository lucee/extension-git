package ch.rasia.extension.resource.git;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevWalkUtils;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.TreeWalk;

import lucee.commons.io.res.Resource;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.config.Config;

public class GitFactory {

	private Git git;
	private URL url;
	private File localCacheDirectory;
	private String branchName;
	private File localBranch;
	private long TIMEOUT=1*1000;
	private long last=0;
	
	private static Map<String, GitFactory> factories=new HashMap<>();
	private static File temp;


	public static GitFactory getInstance(Config config, GitProperties props) throws Exception {
		GitFactory f = factories.get(props.toString());
		if(f!=null) {
			f.ping();
			return f;
		}
		f=new GitFactory(getRoot(config), props.getRepository(), props.getBranch(),props.getCredential());
		factories.put(props.toString(), f);
		return f;
	}

	public GitFactory(File localCacheDirectory, URL url, String branchName, Credential credential) throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		log(url+"->"+branchName);
		this.url = url;
		this.localCacheDirectory = localCacheDirectory;
		this.branchName = branchName;
		String hash = GitUtil.create64BitHashAsString(url.toExternalForm());
		
		File localRepo = new File(localCacheDirectory, hash);
		localBranch = new File(localRepo, branchName);
		
		if (localBranch.isDirectory()) {
			try {
				if(git==null) {
					
					git = Git.open(localBranch);
					git.checkout().setName(branchName).call();
				}
				pull(true);
			}
			catch (Exception e) {
				e.printStackTrace();
				GitUtil.delete(localBranch);
				localBranch.mkdirs();
				git=null;
			}
		} 
		else {
			localBranch.mkdirs();
		}
		

		if (git == null) {
			log("clone repo ");
			CloneCommand cc = Git.cloneRepository();
			if(credential!=null) cc.setCredentialsProvider(credential.toCredentialsProvider());
			git = cc.setURI(url.toExternalForm()).setDirectory(localBranch).setBranch(branchName)
						// .setCloneAllBranches(true)
						.call();
			
			log("cloned repo ");
			git.checkout().setName(branchName).call();
		}
		
		// commit info to a specific file
		log("pom.xml:"+lastModified("loader/pom.xml"));
		
		long start=System.currentTimeMillis();
		RevCommit[] commits = getCommitInfo();
		print("local",commits[0]);
		print("remote",commits[0]);
		log("exe:"+(System.currentTimeMillis()-start));
		
		// 
		/*RevCommit youngestCommit = null;   
		List<Ref> branches = git.branchList().setListMode(ListMode.ALL).call();
		try(RevWalk walk = new RevWalk(git.getRepository())) {
		    for(Ref branch : branches) {
		    	log("branch:"+branch.getName());
		    	RevCommit commit = walk.parseCommit(branch.getObjectId());
		        if(youngestCommit==null || commit.getAuthorIdent().getWhen().compareTo(
		           youngestCommit.getAuthorIdent().getWhen()) > 0)
		           youngestCommit = commit;
		    }
		}
		log("message:"+youngestCommit.getFullMessage());
		log(":"+new Date(youngestCommit.getCommitTime()*1000L));
		//listRepositoryContents(git.getRepository());
		 
		 */
		
	
	}
	
	private void print(String label, RevCommit revCommit) {
		log("-------- "+label+" ---------");
		log(revCommit.getId().getName());
		log(revCommit.getFullMessage());
		log(new Date(revCommit.getCommitTime()*1000L)+"");
		
	}

	private void ping() {
		long now=System.currentTimeMillis();
		if((last+TIMEOUT)<now) {
			try {
				last=now;
				pull(true);
			}
			catch (Exception e) {
				// TODO remove exception
				e.printStackTrace();
			}
		}
	}
	
	public boolean behind() {
		try {
			RevCommit[] commits = getCommitInfo();
			return commits[0].getCommitTime()<commits[1].getCommitTime();
		}
		catch (Exception e) {
			return true;
		}
	}

	public RevCommit[] getCommitInfo() throws GitAPIException, MissingObjectException, IncorrectObjectTypeException, IOException {
		RevCommit local = null, remote = null;   
		List<Ref> branches = git.branchList().setListMode(ListMode.ALL).call();
		try(RevWalk walk = new RevWalk(git.getRepository())) {
		    for(Ref branch : branches) {
		    	if(("refs/heads/"+branchName).equals(branch.getName())) local=walk.parseCommit(branch.getObjectId());
		    	else if(("refs/remotes/origin/"+branchName).equals(branch.getName())) remote=walk.parseCommit(branch.getObjectId());
		    	
		        /*if(youngestCommit==null || commit.getAuthorIdent().getWhen().compareTo(
		           youngestCommit.getAuthorIdent().getWhen()) > 0)
		           youngestCommit = commit;;*/
		    }
		}
		return new RevCommit[]{local,remote};
	}
	
	
	
	
	public long lastModified(String path) throws NoHeadException, GitAPIException {
		Iterator<RevCommit> it = git.log().addPath( path ).call().iterator();
		RevCommit rc=null;
		while(it.hasNext()) {
			rc = it.next();
			break;
		}
		return rc==null?0:rc.getCommitTime()*1000L;
	}

	public void pull(boolean onlyIfNecessary) throws IOException, RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException, CheckoutConflictException, GitAPIException {
		log("check pull repo ");
		if(!onlyIfNecessary || behind()) {
			log("pull repo ");
			long start=System.currentTimeMillis();
			git.pull().call();
			log("pulled repo : "+(System.currentTimeMillis()-start));
		}
	}
	
	

	public File getLocalBranch() {
		return localBranch;
	}

	private void print(PullResult res) {
		log("--------- Pull -----------");
		log("getFetchedFrom:"+res.getFetchedFrom());
		log("toString:"+res);
		log(":"+res.getMergeResult());
		log(":"+res.getFetchResult());
		log(":"+res.getRebaseResult());
		log("sucess:"+res.isSuccessful());
		
	}

	private void print(Status status) {
		log("--------- STATUS -----------");
		log("added:"+status.getAdded().size());
		log("changed:"+status.getChanged().size());
		log("conflicts:"+status.getConflicting().size());
		log("ConflictingStageState:"+status.getConflictingStageState().size());
		log("missing:"+status.getMissing().size());
		log("modified:"+status.getModified().size());
		log("IgnoredNotInIndex:"+status.getIgnoredNotInIndex().size());
		log("UncommittedChanges:"+status.getUncommittedChanges().size());
		log("modified:"+status.getModified().size());
		log("removed:"+status.getRemoved().size());
		log("untracked:"+status.getUntracked().size());
		log("UntrackedFolders:"+status.getUntrackedFolders().size());
	}

	void calculateDivergence(Repository repository, Ref local, Ref tracking) throws IOException {
		RevWalk walk = new RevWalk(repository);
		try {
			RevCommit localCommit = walk.parseCommit(local.getObjectId());
			RevCommit trackingCommit = walk.parseCommit(tracking.getObjectId());
			walk.setRevFilter(RevFilter.MERGE_BASE);
			walk.markStart(localCommit);
			walk.markStart(trackingCommit);
			RevCommit mergeBase = walk.next();
			walk.reset();
			walk.setRevFilter(RevFilter.ALL);
			log("ahead: " + RevWalkUtils.count(walk, localCommit, mergeBase));
			log("behind: " + RevWalkUtils.count(walk, trackingCommit, mergeBase));
			log("id: " + tracking.getObjectId().getName());
			log("id: " + local.getObjectId().getName());
			log("id: " + tracking.getName());
			log("id: " + local.getName());
		} finally {
			walk.dispose();
		}
	}

	public void get(String path) throws IOException, GitAPIException {
		/*
		 * List<Ref> list = git.branchList().call(); Iterator<Ref> it = list.iterator();
		 * while(it.hasNext()) { Ref ref = it.next();
		 * log("- "+ref.getName()+":"+ref.getObjectId().getName()); }
		 */
		listRepositoryContents(git.getRepository());

		// log(git.getRepository().getBranch());
	}

	private static void listRepositoryContents(Repository repository) throws IOException {
		
		
		
		Ref head = repository.getRef("HEAD");

		// a RevWalk allows to walk over commits based on some filtering that is defined
		RevWalk walk = new RevWalk(repository);

		RevCommit commit = walk.parseCommit(head.getObjectId());
		RevTree tree = commit.getTree();
		System.out.println("Having tree: " + tree);

		// now use a TreeWalk to iterate over all files in the Tree recursively
		// you can set Filters to narrow down the results if needed
		TreeWalk treeWalk = new TreeWalk(repository);
		treeWalk.addTree(tree);
		// treeWalk.setRecursive(true);
		while (treeWalk.next()) {
			// treeWalk.
			System.out.println("found: " + treeWalk.getPathString());
		}
	}

	/*public static void main(String[] args) throws Exception {
		log("---- Factory ----");

		GitFactory gf = new GitFactory(new File("/Users/mic/Tmp3/git"), new URL("https://github.com/lucee/Lucee.git"),
				"5.3");

		List<Ref> branches = gf.getBranches();
		Iterator<Ref> it = branches.iterator();
		Ref ref;
		log("Branches:");
		while (it.hasNext()) {
			ref = it.next();
			log("- " + ref.getName() + ":" + ref.getObjectId().getName());
		}

		gf.get("");

	}*/

	private List<Ref> getBranches() throws GitAPIException {

		return git.branchList().call();
	}


	private static File getRoot(Config config) {
		if(temp==null){
			CFMLEngine e = CFMLEngineFactory.getInstance();
			if(config==null) config=CFMLEngineFactory.getInstance().getThreadConfig();
			String cid="";
			
			
			
			
			if(config!=null){
				cid=config.getIdentification().getId();
				Resource t = config.getTempDirectory();
				if(t instanceof File) temp=(File) t;
				System.err.println("t1:"+t);temp=null;
			}
			
			if(temp==null) {
				Resource t = e.getResourceUtil().getTempDirectory();
				if(t instanceof File) temp=(File) t;
				System.err.println("t2:"+t);temp=null;
			}
			
			if(temp==null) {
				temp=lucee.loader.util.Util.getTempDirectory();
				System.err.println("t3:"+temp);
			}
						
			temp=new File(temp,"gitres");
			temp=new File(temp,e.getSystemUtil().hash64b(cid));
			if(!temp.exists())temp.mkdirs();
		}
		return temp;
	}
	
	private void log(String msg) {
		System.err.println(msg);
	}
	
}
