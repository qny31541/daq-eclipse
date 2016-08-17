package org.eclipse.scanning.api.scan.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.scanning.api.INamedNode;

/**
 * An old fashioned singleton pattern because this interacts with 
 * the objects like ScannableContol etc. being made by spring, nicely.
 * 
 * @author Matthew Gerring
 *
 */
public class ControlFactory extends AbstractControl {

	private static ControlFactory instance;
	
	private final List<INamedNode> content;

	private ControlFactory() {
		instance = this;
		this.content = new ArrayList<>(37);
	}
	public static ControlFactory getInstance() {
		if (instance==null) new ControlFactory();
		return instance;
	}
	public static void setInstance(ControlFactory instance) {
		ControlFactory.instance = instance;
	}
	
	public void add(INamedNode object) {
		content.add(object);
	}
	public boolean isEmpty() {
		return content.isEmpty();
	}
	
	/**
	 * A call to this method tells the factory to validate by reading
	 * the groups and controls created in spring. 
	 * 
	 * It is done this way because at the point where spring creates the 
	 * objects, they might not yet have their names and other details set.
	 */
	public void build() {
		
		final List<INamedNode> children = new ArrayList<>();
		// Parse all names into a map, if names are repeated, throw 
		// an exception. All names in the table must be unique, even scannables
		// and groups must not have colliding names.
		Set<String> names = new HashSet<>(content.size());
		for (INamedNode iNameable : content) {
			String name = iNameable.getName();
			if (names.contains(name)) throw new IllegalArgumentException("The name '"+name+"' is already used!");
			names.add(name);
			
			if (iNameable instanceof ControlGroup) {
				children.add(iNameable);
			}
		}
		setName("Controls"); 
		setChildren(children.toArray(new INamedNode[children.size()]));
	}

}
