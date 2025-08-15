package ch.rasia.extension.resource.git;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import lucee.commons.io.res.Resource;
import lucee.commons.io.res.ResourceLock;
import lucee.commons.io.res.ResourceProvider;
import lucee.commons.io.res.Resources;
import lucee.commons.lang.types.RefString;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.PageContext;
import lucee.runtime.ext.function.BIF;
import lucee.runtime.listener.ApplicationContext;
import lucee.runtime.net.s3.Properties;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Excepton;

public class GitResourceProvider implements ResourceProvider {

	private static Credential ENV_CREDENIAL;
	private String scheme="git";
	private Map arguments;

	private int cache=10000;
	private int lockTimeout=20000;
	private ResourceLock lock;
	private boolean initEnvCredential=true;
	
	@Override
	public ResourceProvider init(String scheme,Map arguments) {
		if(!Util.isEmpty(scheme))this.scheme=scheme;
		
		if(arguments!=null) {
			this.arguments=arguments;
			
			// lock-timeout
			String strTimeout=(String) arguments.get("lock-timeout");
			if(strTimeout!=null) {
				lockTimeout=toIntValue(strTimeout,lockTimeout);
			}
			// cache
			String strCache=(String) arguments.get("cache");
			if(strCache!=null) {
				cache=toIntValue(strCache,cache);
			}
		}
		
		return this;
	}

	@Override
	public boolean isAttributesSupported() {
		return false;
	}

	@Override
	public boolean isCaseSensitive() {
		return true;
	}

	@Override
	public boolean isModeSupported() {
		return false;
	}

	@Override
	public String getScheme() {
		return scheme;
	}
	
	@Override
	public Map<String, String> getArguments() {
		return arguments;
	}

	@Override
	public void lock(Resource res) throws IOException {
		lock.lock(res);
	}

	@Override
	public void read(Resource res) throws IOException {
		lock.read(res);
	}

	@Override
	public void setResources(Resources res) {
		lock=res.createResourceLock(lockTimeout,true);
	}

	@Override
	public void unlock(Resource res) {
		lock.unlock(res);
	}

	@Override
	public Resource getResource(String path) {
		CFMLEngine engine = CFMLEngineFactory.getInstance();
		path=engine.getResourceUtil().removeScheme(scheme, path);
		
		GitProperties props=getGitProperties();
		
		try {
			path=translatePath(props,path);
			
			
			if(props.getCredential()==null) {
				if(initEnvCredential) {
					// env var
					String usr=GitUtil.getSystemPropOrEnvVar("lucee.git.username", null);
					if(Util.isEmpty(usr, true)) 
						usr=GitUtil.getSystemPropOrEnvVar("lucee.git.user", null);
					
					String pass=GitUtil.getSystemPropOrEnvVar("lucee.git.password", null);
					if(Util.isEmpty(pass, true)) 
						pass=GitUtil.getSystemPropOrEnvVar("lucee.git.pass", null);
					if(!Util.isEmpty(usr,true) || !Util.isEmpty(pass,true)) ENV_CREDENIAL=new Credential(usr, pass);
					initEnvCredential=false;
				}
				props.setCredential(new GitProperties.Data<Credential>(GitProperties.SOURCE_APP_CFC,ENV_CREDENIAL));
			}
			
			
			path=engine.getResourceUtil().translatePath(path, false, false);
			if(props.getRepositoryData()==null) throw engine.getExceptionUtil()
			.createApplicationException("Missing repository URL, you can define the repository URL in the Application.cfc or with the path itself.");
			GitFactory factory = GitFactory.getInstance(null,props);
			return new GitResource(engine,this, factory, props, path);
		}
		catch (Exception e) {
			throw engine.getExceptionUtil().createPageRuntimeException(engine.getCastUtil().toPageException(e));
		}
	}

	private GitProperties getGitProperties() {
		GitProperties props=new GitProperties();
		
		// lucee.runtime.functions.system.GetApplicationSettings
		CFMLEngine engine = CFMLEngineFactory.getInstance();
		PageContext pc = engine.getThreadPageContext();
		if(pc!=null) {
			
			try {
				BIF bif = CFMLEngineFactory.getInstance().getClassUtil().loadBIF(pc, "lucee.runtime.functions.system.GetApplicationSettings");
				Struct sct=(Struct)bif.invoke(pc, new Object[0]);
				if(sct!=null) {
					sct=engine.getCastUtil().toStruct(sct.get("git"));
					System.err.println(sct);
					if(sct!=null) {
						
					// Credentials
						// user
						String user = engine.getCastUtil().toString(sct.get("username",null),null);
						if(Util.isEmpty(user)) user = engine.getCastUtil().toString(sct.get("user",null),null);
						// pass
						String pass = engine.getCastUtil().toString(sct.get("password",null),null);
						if(Util.isEmpty(pass)) user = engine.getCastUtil().toString(sct.get("pass",null),null);
						
						if(!Util.isEmpty(user,true) && !Util.isEmpty(pass,true)) {
							props.setCredential(
									new GitProperties.Data<Credential>(GitProperties.SOURCE_APP_CFC,
									new Credential(user.trim(),pass.trim())
									)
							);
						}
					
					// Repository
						String repo = engine.getCastUtil().toString(sct.get("repository",null),null);
						if(Util.isEmpty(repo)) repo = engine.getCastUtil().toString(sct.get("repo",null),null);
						if(!Util.isEmpty(repo,true)) {
							props.setRepository(new GitProperties.Data<URL>(GitProperties.SOURCE_APP_CFC,toURL(repo)));
						}

					// Branch
						String branch = engine.getCastUtil().toString(sct.get("branch",null),null);
						if(!Util.isEmpty(branch,true)) {
							props.setBranch(new GitProperties.Data<String>(GitProperties.SOURCE_APP_CFC,branch.trim()));
						}
					}
				}
			} 
			catch (Exception e) {
				System.err.println("exception:");
				// TODO  remove
				e.printStackTrace();
			}
		}
		return props;
	}

	private static URL toURL(String repo) throws MalformedURLException {
		if(Util.isEmpty(repo,true)) {
			return null;
		}
		repo=repo.trim();
		String lc=repo.toLowerCase();
		if(!lc.startsWith("http://") && !lc.startsWith("https://"))
			repo="https://"+repo;
		URL url=new URL(repo);
		System.out.println("url:"+url);
		return url;
	}

	public static String translatePath(GitProperties props, String path) throws MalformedURLException {
		
		int index=path.indexOf('@');
		
		// branch
		if(index!=-1) {
			props.setBranch(new GitProperties.Data(GitProperties.SOURCE_PATH,path.substring(0, index)));
			path=path.substring(index+1);
		}
		
		// repositoty
		index=path.indexOf('!');
		if(index!=-1) {
			URL url = toURL(path.substring(0, index));
			if(url!=null)props.setRepository(new GitProperties.Data<URL>(GitProperties.SOURCE_PATH,url));
			path=path.substring(index+1);
		}
		
		
		/*
		// host
		index=path.indexOf('/');
		String host=path.substring(0, index);
		path=path.substring(index);
		
		// repo
		index=path.indexOf('!');
		String repo=path.substring(0, index);
		path=path.substring(index+1);
		*/
		
		
		if(path.startsWith("/")) path=path.substring(1);
		
		System.err.println(props);
		
		return path;
	}

	private int toIntValue(String str, int defaultValue) {
		try{
			return Integer.parseInt(str);
		}
		catch(Throwable t){
			if(t instanceof ThreadDeath) throw (ThreadDeath)t;
			return defaultValue;
		}
	}
}
