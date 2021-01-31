package dev.fulmineo.companion_bats.item;

public enum CompanionBatClass {
	INFERNO,
	VAMPIRE,
	LOOTER,
	KNIGHT,
	ALCHEMIST,
	DUELIST,
	NINJA;

	public String getExpTagName(){
		return this.toString().toLowerCase()+"_exp";
	}
}
