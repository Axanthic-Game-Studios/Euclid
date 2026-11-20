package euclid.load;

/** One model of a blockstate key (a key can have several, the game picks one at random): one row of the list. */
public final class Row {
	public final String key;       // normalized property key of the blockstate ("" = no properties)
	public final int weight;       // weight as written in the blockstate, 0 = none given (the game then uses 1)
	public final String label;     // text of the model row
	public final String model;     // model reference as in the blockstate
	public final int x, y;         // rotation as in the blockstate

	Row(String key, int weight, String model, int x, int y) {
		this.key = key;
		this.weight = weight;
		this.model = model;
		this.x = x;
		this.y = y;
		this.label = Models.shortModel(model);
	}
}
