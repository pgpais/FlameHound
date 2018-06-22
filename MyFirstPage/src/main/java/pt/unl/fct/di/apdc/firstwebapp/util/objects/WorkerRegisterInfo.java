package pt.unl.fct.di.apdc.firstwebapp.util.objects;

public class WorkerRegisterInfo {
	public String username;
	public String password;
	public String registerUsername;
	public String registerToken;
	public String tokenId;
	public String entity;
	
	public WorkerRegisterInfo() {

	}

	public WorkerRegisterInfo(String tokenId, String username, String password, String registerUsername, String registerToken, String entity) {
		this.tokenId = tokenId;
		this.username = username;
		this.password = password;
		this.registerToken = registerToken;
		this.registerUsername = registerUsername;
		this.entity = entity;
	}
}