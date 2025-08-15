package ch.rasia.extension.resource.git;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public class Credential {
	public final String username;
	public final String password;
	
	public Credential(String username, String password) {
		this.username = username;
		this.password = password;
	}

	public CredentialsProvider toCredentialsProvider() {
		return new UsernamePasswordCredentialsProvider( username, password) ;
	}
}
