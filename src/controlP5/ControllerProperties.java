/* 
 *  controlP5 is a processing gui library.
 * 
 * Copyright (C)  2006-2012 by Andreas Schlegel
 * Copyright (C)  2015 by Jeremy Laviole
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 * 
 * 
 */
package controlP5;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import processing.core.PApplet;

/**
 * Values of controllers can be stored inside properties files which can be
 * saved to file or memory.
 * 
 * @example controllers/ControlP5properties
 */
public class ControllerProperties {
	
	/**
	 * @exclude
	 */
	public enum Format {
		SERIALIZED("ser"), XML("xml"), JSON("json");

		final String extension;

		PropertiesStorageFormat format;

		Format(String theExtension) {
			extension = theExtension;
		}

		protected void set(PropertiesStorageFormat theStorageFormat) {
			format = theStorageFormat;
		}

		protected PropertiesStorageFormat get() {
			return format;
		}

		protected void compile(String thePropertiesPath, Set<ControllerProperty> theProperties) {
			if (!thePropertiesPath.endsWith("." + extension)) {
				thePropertiesPath = thePropertiesPath + "." + extension;
			}
			get().compile(theProperties, thePropertiesPath);
		}

	}

	{
		Format.SERIALIZED.set(new SerializedFormat());
		Format.XML.set(new XMLFormat());
		Format.JSON.set(new JSONFormat());
	}

	public final static int OPEN = 0;

	public final static int CLOSE = 1;

	public static String defaultName = "controlP5";

	public static Format format;

	/**
	 * all ControllerProperties will be stored inside Map allProperties.
	 * ControllerProperties need to be unique or will otherwise be overwritten.
	 * 
	 * A hashSet containing names of PropertiesSets is assigned to each
	 * ControllerProperty. HashSets are used instead of ArrayList to only allow
	 * unique elements.
	 */

	private Map<ControllerProperty, HashSet<String>> allProperties;

	/**
	 * A set of unique property-set names.
	 */
	private Set<String> allSets;

	private ControlP5 controlP5;

	private String _myDefaultSetName = "default";

	public static final Logger logger = Logger.getLogger(ControllerProperties.class.getName());

	private Map<String, Set<ControllerProperty>> _mySnapshots;

	public ControllerProperties(ControlP5 theControlP5) {
		controlP5 = theControlP5;
		setFormat(Format.SERIALIZED);
		allProperties = new HashMap<ControllerProperty, HashSet<String>>();
		allSets = new HashSet<String>();
		addSet(_myDefaultSetName);
		_mySnapshots = new LinkedHashMap<String, Set<ControllerProperty>>();
	}

	public Map<ControllerProperty, HashSet<String>> get() {
		return allProperties;
	}

	/**
	 * adds a property based on names of setter and getter methods of a
	 * controller.
	 * 
	 * @param thePropertySetter
	 * @param thePropertyGetter
	 */
	public ControllerProperty register(ControllerInterface<?> theController, String thePropertySetter, String thePropertyGetter) {
		ControllerProperty p = new ControllerProperty(theController, thePropertySetter, thePropertyGetter);
		if (!allProperties.containsKey(p)) {
			// register a new property with the main properties container
			allProperties.put(p, new HashSet<String>());
			// register the property wit the default properties set
			allProperties.get(p).add(_myDefaultSetName);
		}
		return p;
	}

	/**
	 * registering a property with only one parameter assumes that there is a
	 * setter and getter function present for the Controller. register("value")
	 * for example would create a property reference to setValue and getValue.
	 * Notice that the first letter of value is being capitalized.
	 * 
	 * @param theProperty
	 * @return
	 */
	public ControllerProperties register(ControllerInterface<?> theController, String theProperty) {
		theProperty = Character.toUpperCase(theProperty.charAt(0)) + theProperty.substring(1);
		register(theController, "set" + theProperty, "get" + theProperty);
		return this;
	}

	public ControllerProperties remove(ControllerInterface<?> theController, String theSetter, String theGetter) {
		ControllerProperty cp = new ControllerProperty(theController, theSetter, theGetter);
		allProperties.remove(cp);
		return this;
	}

	public ControllerProperties remove(ControllerInterface<?> theController) {
		ArrayList<ControllerProperty> list = new ArrayList<ControllerProperty>(allProperties.keySet());
		for (ControllerProperty cp : list) {
			if (cp.getController().equals(theController)) {
				allProperties.remove(cp);
			}
		}
		return this;
	}

	public ControllerProperties remove(ControllerInterface<?> theController, String theProperty) {
		return remove(theController, "set" + theProperty, "get" + theProperty);
	}

	public List<ControllerProperty> get(ControllerInterface<?> theController) {
		List<ControllerProperty> props = new ArrayList<ControllerProperty>();
		List<ControllerProperty> list = new ArrayList<ControllerProperty>(allProperties.keySet());
		for (ControllerProperty cp : list) {
			if (cp.getController().equals(theController)) {
				props.add(cp);
			}
		}
		return props;
	}

	public ControllerProperty getProperty(ControllerInterface<?> theController, String theSetter, String theGetter) {
		ControllerProperty cp = new ControllerProperty(theController, theSetter, theGetter);
		Iterator<ControllerProperty> iter = allProperties.keySet().iterator();
		while (iter.hasNext()) {
			ControllerProperty p = iter.next();
			if (p.equals(cp)) {
				return p;
			}
		}
		// in case the property has not been registered before, it will be
		// registered here automatically - you don't need to call
		// Controller.registerProperty() but can use Controller.getProperty()
		// instead.
		return register(theController, theSetter, theGetter);
	}

	public ControllerProperty getProperty(ControllerInterface<?> theController, String theProperty) {
		theProperty = Character.toUpperCase(theProperty.charAt(0)) + theProperty.substring(1);
		return getProperty(theController, "set" + theProperty, "get" + theProperty);
	}

	public HashSet<ControllerProperty> getPropertySet(ControllerInterface<?> theController) {
		HashSet<ControllerProperty> set = new HashSet<ControllerProperty>();
		Iterator<ControllerProperty> iter = allProperties.keySet().iterator();
		while (iter.hasNext()) {
			ControllerProperty p = iter.next();
			if (p.getController().equals(theController)) {
				set.add(p);
			}
		}
		return set;
	}

	public ControllerProperties addSet(String theSet) {
		allSets.add(theSet);
		return this;
	}

	/**
	 * Moves a ControllerProperty from one set to another.
	 */
	public ControllerProperties move(ControllerProperty theProperty, String fromSet, String toSet) {
		if (!exists(theProperty)) {
			return this;
		}
		if (allProperties.containsKey(theProperty)) {
			if (allProperties.get(theProperty).contains(fromSet)) {
				allProperties.get(theProperty).remove(fromSet);
			}
			addSet(toSet);
			allProperties.get(theProperty).add(toSet);
		}
		return this;
	}

	public ControllerProperties move(ControllerInterface<?> theController, String fromSet, String toSet) {
		HashSet<ControllerProperty> set = getPropertySet(theController);
		for (ControllerProperty cp : set) {
			move(cp, fromSet, toSet);
		}
		return this;
	}

	/**
	 * copies a ControllerProperty from one set to other(s);
	 */
	public ControllerProperties copy(ControllerProperty theProperty, String... theSet) {
		if (!exists(theProperty)) {
			return this;
		}
		for (String s : theSet) {
			allProperties.get(theProperty).add(s);
			if (!allSets.contains(s)) {
				addSet(s);
			}
		}
		return this;
	}

	public ControllerProperties copy(ControllerInterface<?> theController, String... theSet) {
		HashSet<ControllerProperty> set = getPropertySet(theController);
		for (ControllerProperty cp : set) {
			copy(cp, theSet);
		}
		return this;
	}

	/**
	 * removes a ControllerProperty from one or multiple sets.
	 */
	public ControllerProperties remove(ControllerProperty theProperty, String... theSet) {
		if (!exists(theProperty)) {
			return this;
		}
		for (String s : theSet) {
			allProperties.get(theProperty).remove(s);
		}
		return this;
	}

	public ControllerProperties remove(ControllerInterface<?> theController, String... theSet) {
		HashSet<ControllerProperty> set = getPropertySet(theController);
		for (ControllerProperty cp : set) {
			remove(cp, theSet);
		}
		return this;
	}

	/**
	 * stores a ControllerProperty in one particular set only.
	 */
	public ControllerProperties only(ControllerProperty theProperty, String theSet) {
		// clear all the set-references for a particular property
		allProperties.get(theProperty).clear();
		// add theSet to the empty collection of sets for this particular
		// property
		allProperties.get(theProperty).add(theSet);
		return this;
	}

	ControllerProperties only(ControllerInterface<?> theController, String... theSet) {
		return this;
	}

	private boolean exists(ControllerProperty theProperty) {
		return allProperties.containsKey(theProperty);
	}

	public ControllerProperties print() {
		for (Entry<ControllerProperty, HashSet<String>> entry : allProperties.entrySet()) {
			System.out.println(entry.getKey() + "\t" + entry.getValue());
		}
		return this;
	}

	/**
	 * deletes a ControllerProperty from all Sets including the default set.
	 */ 
	public ControllerProperties delete(ControllerProperty theProperty) {
		if (!exists(theProperty)) {
			return this;
		}
		allProperties.remove(theProperty);
		return this;
	}

	private boolean updatePropertyValue(ControllerProperty theProperty) {
		Method method;
		try {
			method = theProperty.getController().getClass().getMethod(theProperty.getGetter());
			Object value = method.invoke(theProperty.getController());
			theProperty.setType(method.getReturnType());
			theProperty.setValue(value);
			if (checkSerializable(value)) {
				return true;
			}
		} catch (Exception e) {
			logger.severe("" + e);
		}
		return false;
	}

	private boolean checkSerializable(Object theProperty) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ObjectOutputStream stream = new ObjectOutputStream(out);
			stream.writeObject(theProperty);
			stream.close();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * logs all registered properties in memory. Here, clones of properties are
	 * stored inside a map and can be accessed by key using the getLog method.
	 * 
	 * @see controlP5.ControllerProperties#getSnapshot(String)
	 * @param theKey
	 * @return ControllerProperties
	 */
	public ControllerProperties setSnapshot(String theKey) {
		Set<ControllerProperty> l = new HashSet<ControllerProperty>();
		for (ControllerProperty cp : allProperties.keySet()) {
			updatePropertyValue(cp);
			try {
				l.add((ControllerProperty) cp.clone());
			} catch (CloneNotSupportedException e) {
				// TODO Auto-generated catch block
			}
		}
		_mySnapshots.put(theKey, l);
		return this;
	}

	/**
	 * convenience method, setSnapshot(String) also works here since it will
	 * override existing log with the same key.
	 */
	public ControllerProperties updateSnapshot(String theKey) {
		return setSnapshot(theKey);
	}

	/**
	 * removes a snapshot by key.
	 */
	public ControllerProperties removeSnapshot(String theKey) {
		_mySnapshots.remove(theKey);
		return this;
	}

	ControllerProperties setSnapshot(String theKey, String... theSets) {
		return this;
	}

	/**
	 * saves a snapshot into your sketch's sketch folder.
	 */
	public ControllerProperties saveSnapshot(String theKey) {
		saveSnapshotAs(controlP5.papplet.sketchPath(theKey), theKey);
		return this;
	}

	/**
	 * saves a snapshot to the file with path given by the first parameter
	 * (thePropertiesPath).
	 */
	public ControllerProperties saveSnapshotAs(String thePropertiesPath, String theKey) {
		Set<ControllerProperty> log = _mySnapshots.get(theKey);
		if (log == null) {
			return this;
		}
		thePropertiesPath = controlP5.checkPropertiesPath(thePropertiesPath);
		format.compile(thePropertiesPath, log);
		return this;
	}

	/**
	 * restores properties previously stored as snapshot in memory.
	 * @see controlP5.ControllerProperties#setSnapshot(String)
	 */
	public ControllerProperties getSnapshot(String theKey) {
		Set<ControllerProperty> l = _mySnapshots.get(theKey);
		if (l != null) {
			for (ControllerProperty cp : l) {
				ControllerInterface<?> ci = controlP5.getController(cp.getAddress());
				ci = (ci == null) ? controlP5.getGroup(cp.getAddress()) : ci;
				Method method;
				try {
					method = ci.getClass().getMethod(cp.getSetter(), new Class[] { cp.getType() });
					method.setAccessible(true);
					method.invoke(ci, new Object[] { cp.getValue() });
				} catch (Exception e) {
					logger.severe(e.toString());
				}
			}
		}
		return this;
	}

	/**
	 * properties stored in memory can be accessed by index,
	 * getSnapshotIndices() returns the index of the snapshot list.
	 */ 
	public ArrayList<String> getSnapshotIndices() {
		return new ArrayList<String>(_mySnapshots.keySet());
	}

	/**
	 * load properties from the default properties file 'controlP5.properties'
	 */
	public boolean load() {
		return load(controlP5.papplet.sketchPath(defaultName + "." + format.extension));
	}

	public boolean load(String thePropertiesPath) {
		thePropertiesPath = controlP5.checkPropertiesPath(thePropertiesPath);
		for (Format myFormat : Format.values()) {
			if (thePropertiesPath.toLowerCase().endsWith(myFormat.extension)) {
				return myFormat.get().load(thePropertiesPath);
			}
		}
		return false;
	}

	/**
	 * use ControllerProperties.SERIALIZED, ControllerProperties.XML or
	 * ControllerProperties.JSON as parameter.
	 */
	public void setFormat(Format theFormatId) {
		format = theFormatId;
	}

	/**
	 * saves all registered properties into the default 'controlP5.properties'
	 * file into your sketch folder.
	 */
	public boolean save() {
		System.out.println("saving with format " + format + " (" + format.extension + ") " + controlP5.papplet.sketchPath(defaultName));
		format.compile(controlP5.papplet.sketchPath(defaultName), allProperties.keySet());
		return true;
	}

	/**
	 * saves all registered properties into a file specified by parameter
	 * thePropertiesPath.
	 */
	public boolean saveAs(String thePropertiesPath) {
		thePropertiesPath = controlP5.checkPropertiesPath(thePropertiesPath);
		format.compile(thePropertiesPath, allProperties.keySet());
		return true;
	}

	/**
	 * saves a list of properties sets into a file specified by parameter
	 * thePropertiesPath.
	 */
	public boolean saveAs(String thePropertiesPath, String... theSets) {
		thePropertiesPath = controlP5.checkPropertiesPath(thePropertiesPath);
		HashSet<ControllerProperty> sets = new HashSet<ControllerProperty>();
		Iterator<ControllerProperty> iter = allProperties.keySet().iterator();
		while (iter.hasNext()) {
			ControllerProperty p = iter.next();
			if (allProperties.containsKey(p)) {
				HashSet<String> set = allProperties.get(p);
				for (String str : set) {
					for (String s : theSets) {
						if (str.equals(s)) {
							sets.add(p);
						}
					}
				}
			}
		}
		format.compile(thePropertiesPath, sets);
		return true;
	}

	/**
	 * @exclude
	 * {@inheritDoc}
	 */
	public String toString() {
		String s = "";
		s += this.getClass().getName() + "\n";
		s += "total num of properties:\t" + allProperties.size() + "\n";
		for (ControllerProperty c : allProperties.keySet()) {
			s += "\t" + c + "\n";
		}
		s += "total num of sets:\t\t" + allSets.size() + "\n";
		for (String set : allSets) {
			s += "\t" + set + "\n";
		}
		return s;
	}

	interface PropertiesStorageFormat {
		public void compile(Set<ControllerProperty> theProperties, String thePropertiesPath);

		public boolean load(String thePropertiesPath);

	}

	class XMLFormat implements PropertiesStorageFormat {
		public void compile(Set<ControllerProperty> theProperties, String thePropertiesPath) {
			System.out.println("Dont use the XMLFormat yet, it is not fully implemented with 0.5.9, use SERIALIZED instead.");
			System.out.println("Compiling with XMLFormat");
			StringBuffer xml = new StringBuffer();
			xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			xml.append("<properties name=\"" + thePropertiesPath + "\">\n");
			for (ControllerProperty cp : theProperties) {
				if (cp.isActive()) {
					updatePropertyValue(cp);
					xml.append(getXML(cp));
				}
			}
			xml.append("</properties>");
			controlP5.papplet.saveStrings(thePropertiesPath, PApplet.split(xml.toString(), "\n"));
			System.out.println("saving xml, " + thePropertiesPath);
		}

		public boolean load(String thePropertiesPath) {
			String s;
			try {
				s = PApplet.join(controlP5.papplet.loadStrings(thePropertiesPath), "\n");
			} catch (Exception e) {
				logger.warning(thePropertiesPath + ", file not found.");
				return false;
			}
			System.out.println("loading xml \n" + s);
			try {
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();
				InputSource is = new InputSource();
				is.setCharacterStream(new StringReader(s));
				Document doc = db.parse(is);
				doc.getDocumentElement().normalize();
				NodeList nodeLst = doc.getElementsByTagName("property");
				for (int i = 0; i < nodeLst.getLength(); i++) {
					Node node = nodeLst.item(i);
					if (node.getNodeType() == Node.ELEMENT_NODE) {
						Element fstElmnt = (Element) node;
						String myAddress = getElement(fstElmnt, "address");
						String mySetter = getElement(fstElmnt, "setter");
						String myType = getElement(fstElmnt, "type");
						String myValue = getElement(fstElmnt, "value");
						// String myClass = getElement(fstElmnt, "class");
						// String myGetter = getElement(fstElmnt, "getter");
						try {
							System.out.print("setting controller " + myAddress + "   ");
							ControllerInterface<?> ci = controlP5.getController(myAddress);
							ci = (ci == null) ? controlP5.getGroup(myAddress) : ci;
							System.out.println(ci);
							Method method;
							try {
								Class<?> c = getClass(myType);
								System.out.println(myType + " / " + c);
								method = ci.getClass().getMethod(mySetter, new Class[] { c });
								method.setAccessible(true);
								method.invoke(ci, new Object[] { getValue(myValue, myType, c) });
							} catch (Exception e) {
								logger.severe(e.toString());
							}
						} catch (Exception e) {
							logger.warning("skipping a property, " + e);
						}
					}

				}
			} catch (SAXException e) {
				logger.warning("SAXException, " + e);
				return false;
			} catch (IOException e) {
				logger.warning("IOException, " + e);
				return false;
			} catch (ParserConfigurationException e) {
				logger.warning("ParserConfigurationException, " + e);
				return false;
			}
			return true;
		}

		private Object getValue(String theValue, String theType, Class<?> theClass) {
			if (theClass == int.class) {
				return Integer.parseInt(theValue);
			} else if (theClass == float.class) {
				return Float.parseFloat(theValue);
			} else if (theClass == boolean.class) {
				return Boolean.parseBoolean(theValue);
			} else if (theClass.isArray()) {
				System.out.println("this is an array: " + theType + ", " + theValue + ", " + theClass);
				int dim = 0;
				while (true) {
					if (theType.charAt(dim) != '[' || dim >= theType.length()) {
						break;
					}
					dim++;
				}
			} else {
				System.out.println("is array? " + theClass.isArray());
			}
			return theValue;
		}

		private Class<?> getClass(String theType) {
			if (theType.equals("int")) {
				return int.class;
			} else if (theType.equals("float")) {
				return float.class;
			} else if (theType.equals("String")) {
				return String.class;
			}
			try {
				return Class.forName(theType);
			} catch (ClassNotFoundException e) {
				logger.warning("ClassNotFoundException, " + e);
			}
			return null;
		}

		private String getElement(Element theElement, String theName) {
			NodeList fstNmElmntLst = theElement.getElementsByTagName(theName);
			Element fstNmElmnt = (Element) fstNmElmntLst.item(0);
			NodeList fstNm = fstNmElmnt.getChildNodes();
			return ((Node) fstNm.item(0)).getNodeValue();
		}

		public String getXML(ControllerProperty theProperty) {
			// Mapping Between JSON and Java Entities
			// http://code.google.com/p/json-simple/wiki/MappingBetweenJSONAndJavaEntities
			String s = "\t<property>\n";
			s += "\t\t<address>" + theProperty.getAddress() + "</address>\n";
			s += "\t\t<class>" + CP.formatGetClass(theProperty.getController().getClass()) + "</class>\n";
			s += "\t\t<setter>" + theProperty.getSetter() + "</setter>\n";
			s += "\t\t<getter>" + theProperty.getGetter() + "</getter>\n";
			s += "\t\t<type>" + CP.formatGetClass(theProperty.getType()) + "</type>\n";
			s += "\t\t<value>" + cdata(OPEN, theProperty.getValue().getClass())
					+ (theProperty.getValue().getClass().isArray() ? CP.arrayToString(theProperty.getValue()) : theProperty.getValue())
					+ cdata(CLOSE, theProperty.getValue().getClass()) + "</value>\n";
			s += "\t</property>\n";
			return s;
		}

		private String cdata(int a, Class<?> c) {
			if (c == String.class || c.isArray()) {
				return (a == OPEN ? "<![CDATA[" : "]]>");
			}
			return "";
		}
	}

	class JSONFormat implements PropertiesStorageFormat {
		public void compile(Set<ControllerProperty> theProperties, String thePropertiesPath) {
			System.out.println("Dont use the JSONFormat yet, it is not fully implemented with 0.5.9, use SERIALIZED instead.");
			System.out.println("Compiling with JSONFormat");
		}

		public boolean load(String thePropertiesPath) {
			return false;
		}
	}

	class SerializedFormat implements PropertiesStorageFormat {

		public boolean load(String thePropertiesPath) {
			try {
				FileInputStream fis = new FileInputStream(thePropertiesPath);
				ObjectInputStream ois = new ObjectInputStream(fis);
				int size = ois.readInt();
				logger.info("loading " + size + " property-items.");

				for (int i = 0; i < size; i++) {
					try {
						ControllerProperty cp = (ControllerProperty) ois.readObject();
						ControllerInterface<?> ci = controlP5.getController(cp.getAddress());
						ci = (ci == null) ? controlP5.getGroup(cp.getAddress()) : ci;
						ci.setId(cp.getId());
						Method method;
						try {
							method = ci.getClass().getMethod(cp.getSetter(), new Class[] { cp.getType() });
							method.setAccessible(true);
							method.invoke(ci, new Object[] { cp.getValue() });
						} catch (Exception e) {
							logger.severe(e.toString());
						}

					} catch (Exception e) {
						logger.warning("skipping a property, " + e);
					}
				}
				ois.close();
			} catch (Exception e) {
				logger.warning("Exception during deserialization: " + e);
				return false;
			}
			return true;
		}

		public void compile(Set<ControllerProperty> theProperties, String thePropertiesPath) {
			int active = 0;
			int total = 0;
			HashSet<ControllerProperty> propertiesToBeSaved = new HashSet<ControllerProperty>();
			for (ControllerProperty cp : theProperties) {
				if (cp.isActive()) {
					if (updatePropertyValue(cp)) {
						active++;
						cp.setId(cp.getController().getId());
						propertiesToBeSaved.add(cp);
					}
				}
				total++;
			}

			int ignored = total - active;

			try {
				FileOutputStream fos = new FileOutputStream(thePropertiesPath);
				ObjectOutputStream oos = new ObjectOutputStream(fos);

				logger.info("Saving property-items to " + thePropertiesPath);
				oos.writeInt(active);

				for (ControllerProperty cp : propertiesToBeSaved) {
					if (cp.isActive()) {
						oos.writeObject(cp);
					}
				}
				logger.info(active + " items saved, " + (ignored) + " items ignored. Done saving properties.");
				oos.flush();
				oos.close();
				fos.close();
			} catch (Exception e) {
				logger.warning("Exception during serialization: " + e);
			}
		}
	}
}