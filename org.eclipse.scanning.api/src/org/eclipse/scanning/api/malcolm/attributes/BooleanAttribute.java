package org.eclipse.scanning.api.malcolm.attributes;

/**
 * 
 * Encapsulates a boolean attribute as read from a malcolm device
 * 
 * @author Matt Taylor
 *
 */
public class BooleanAttribute extends MalcolmAttribute {
	public static final String BOOLEAN_ID = "malcolm:core/BooleanMeta:";
	
	boolean value;

	@Override
	public Boolean getValue() {
		return value;
	}

	public void setValue(boolean value) {
		this.value = value;
	}
	
}
