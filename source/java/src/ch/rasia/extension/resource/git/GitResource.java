package ch.rasia.extension.resource.git;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;

import ch.rasia.extension.resource.ResourceSupport;
import ch.rasia.extension.resource.git.GitProperties.Data;
import lucee.commons.io.res.Resource;
import lucee.commons.io.res.ResourceProvider;
import lucee.loader.engine.CFMLEngine;

public class GitResource extends ResourceSupport {

	private static final long serialVersionUID = 9073457359783078066L;

	private final File file;
	private final GitFactory factory;
	private final GitProperties props;
	private final String path;
	private GitResourceProvider provider;

	GitResource(CFMLEngine engine, GitResourceProvider provider, GitFactory factory,GitProperties props, String path) {
		this(engine,provider,factory,props,path,null);
	}
	private GitResource(CFMLEngine engine, GitResourceProvider provider, GitFactory factory,GitProperties props, String path, File file) {
		super(engine);
		this.factory=factory;
		this.provider=provider;
		this.props=props;
		this.path=engine.getResourceUtil().translatePath(path,false,false);
		
		this.file=file==null?
				lucee.loader.util.Util.isEmpty(path)?factory.getLocalBranch():new File(factory.getLocalBranch(),path)
				:file;

				/*System.err.println(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::");
				System.err.println("file:"+file);
				System.err.println("path:"+path);
				System.err.println(props);
				System.err.println(getPath());*/
	}

	@Override
	public void createDirectory(boolean arg0) throws IOException {
		throw readOnlyException();
	}

	@Override
	public void createFile(boolean arg0) throws IOException {
		throw readOnlyException();
	}

	@Override
	public boolean exists() {
		return file.exists();
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return new FileInputStream(file);
	}

	@Override
	public int getMode() {
		return 0;
	}

	@Override
	public String getName() {
		return file.getName();
	}

	@Override
	public OutputStream getOutputStream(boolean arg0) throws IOException {
		throw readOnlyException();
	}

	@Override
	public String getParent() {
		if(isRoot()) return null;
		return getPrefix().append(getInnerParent()).toString();
	}

	@Override
	public Resource getParentResource() {
		if(isRoot()) return null;
		return new GitResource(engine,provider,factory,props,getInnerParent());
	}
	
	@Override
	public String getPath() {
		return getPrefix().append(getInnerPath()).toString();
	}

	private String getInnerParent() {
		if(isRoot()) return null;
		int index = path.lastIndexOf('/');
		if(index!=-1) return engine.getResourceUtil().translatePath(path.substring(0, index), true, false);
		return "/";
	}
	
	private String getInnerPath() {
		if(isRoot()) return "/";
		return engine.getResourceUtil().translatePath(path, true, false);
	}

	private boolean isRoot() {
		return path.length()==0;
	}
	
	private StringBuilder getPrefix()  {
		// "5.2@github.com/lucee/Lucee.git!/loader/pom.xml"
		StringBuilder sb=new StringBuilder(provider.getScheme()).append("://");
		
		// branch
		Data<String> branch = props.getBranchData();
		if(branch.source==GitProperties.SOURCE_PATH && !props.isDefaultBranch()) {
			sb.append(props.getBranch()).append('@');
		}
		
		// host / repo
		Data<URL> repo = props.getRepositoryData();
		if(repo.source==GitProperties.SOURCE_PATH) {
			sb.append(repo.data.getHost()).append(repo.data.getFile());
			sb.append('!');
		}
		return sb;
	}

	@Override
	public Resource getRealResource(String realpath) {
		realpath=engine.getResourceUtil().merge(getInnerPath(), realpath);
		if(realpath.startsWith("../"))return null;
		return new GitResource(engine,provider,factory,props,realpath);
	}

	@Override
	public long lastModified() {
		try {
			return factory.lastModified(path); // TODO cache?
		} 
		catch (Exception e) {e.printStackTrace();
			return 0;// TODO pass exception instead?
		}
	}

	@Override
	public ResourceProvider getResourceProvider() {
		return provider;
	}

	@Override
	public boolean isAbsolute() {
		return true;
	}

	@Override
	public boolean isDirectory() {
		return file.isDirectory();
	}

	@Override
	public boolean isFile() {
		return file.isFile();
	}

	@Override
	public boolean isReadable() {
		return file.canRead();
	}

	@Override
	public boolean isWriteable() {
		return file.canWrite();
	}

	@Override
	public long length() {
		return file.length();
	}

	@Override
	public Resource[] listResources() {
		File[] children = file.listFiles();
		if(children==null) return null;
		Resource[] resChildren=new Resource[children.length];
		for(int i=0;i<children.length;i++) {
			resChildren[i]=new GitResource(engine,provider, factory, props, path+File.separatorChar+children[i].getName(),children[i]);
		}
		return resChildren;
	}
	
	@Override
	public String[] list() {
		return file.list();
	}

	@Override
	public void remove(boolean arg0) throws IOException {
		throw readOnlyException();
	}

	@Override
	public boolean setLastModified(long arg0) {
		throw readOnlyRuntimeException();
	}

	@Override
	public void setMode(int arg0) throws IOException {
		throw readOnlyException();
	}

	@Override
	public boolean setReadable(boolean arg0) {
		throw readOnlyRuntimeException();
	}

	@Override
	public boolean setWritable(boolean arg0) {
		throw readOnlyRuntimeException();
	}

	@Override
	public void moveFile(Resource src, Resource dest) throws IOException {
		throw readOnlyException();
	}

	private RuntimeException readOnlyRuntimeException() {
		return new RuntimeException("this action is not allowed for this Resource");
	}
	private IOException readOnlyException() {
		return new IOException("this action is not allowed for this Resource");
	}


}
