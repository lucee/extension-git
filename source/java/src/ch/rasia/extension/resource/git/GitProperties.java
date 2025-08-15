package ch.rasia.extension.resource.git;

import java.net.URL;

public class GitProperties {

	public static short SOURCE_DEFAULT=0;
	public static short SOURCE_APP_CFC=1;
	public static short SOURCE_PATH=2;
	
	private Data<String> _branch=new Data<String>(SOURCE_DEFAULT,"master");
	private Data<URL> _repository;
	private Data<Credential> credential;
	
	public String toString() {
		return 
				"branch:"+getBranch()
				+";repository:"+getRepository()
				;
	}

	public boolean isDefaultBranch() {
		return "master".equals(getBranch());
	}
	
	public static class Data<T> {
		public final short source;
		public final T data;
		
		public Data(short source, T data) {
			this.source=source;
			this.data=data;
		}
	}
	
	public String getBranch() {
		return _branch.data;
	}
	public Data<String> getBranchData() {
		return _branch;
	}
	public void setBranch(Data<String> data) {
		if(data!=null)this._branch=data;
	}
	
	public URL getRepository() {
		if(_repository==null) return null;
		return _repository.data;
	}
	public Data<URL> getRepositoryData() {
		return _repository;
	}
	public void setRepository(Data<URL> data) {
		if(data!=null)this._repository=data;
	}
	
	public Credential getCredential() {
		if(credential==null) return null;
		return credential.data;
	}
	public Data<Credential> getCredentialData() {
		return credential;
	}
	public void setCredential(Data<Credential> data) {
		if(data!=null)this.credential=data;
	}
}
