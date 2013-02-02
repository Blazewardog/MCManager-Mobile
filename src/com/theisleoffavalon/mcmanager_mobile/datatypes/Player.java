package com.theisleoffavalon.mcmanager_mobile.datatypes;

public class Player {

	private String	name;

	private String	ipAddress;

	private Byte[]	picture;

	public Player(String name, String ipAddress, Byte[] picture) {
		this.name = name;
		this.ipAddress = ipAddress;
		this.picture = picture;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getIpAddress() {
		return this.ipAddress;
	}

	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

	public Byte[] getPicture() {
		return this.picture;
	}

	public void setPicture(Byte[] picture) {
		this.picture = picture;
	}

}