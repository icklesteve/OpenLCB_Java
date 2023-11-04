package org.openlcb.cdi.jdom;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;

import org.jdom2.Attribute;
import org.jdom2.Element;
import org.openlcb.cdi.CdiRep;

/**
 * Implement the CdiRep interface using 
 * JDOM for reading the underlying XML.
 *
 * @author  Bob Jacobsen   Copyright 2011
 */
public class JdomCdiRep implements CdiRep {

    public JdomCdiRep(Element root) {
        this.root = root;
    }
    
    public static class Identification implements CdiRep.Identification {    
        @Override
        public String getManufacturer() {
            Element c = id.getChild("manufacturer");
            if (c == null) return null;
            return c.getText();
        }
        @Override
        public String getModel() {
            Element c = id.getChild("model");
            if (c == null) return null;
            return c.getText();
        }
        @Override
        public String getHardwareVersion() {
            Element c = id.getChild("hardwareVersion");
            if (c == null) return null;
            return c.getText();
        }
        @Override
        public String getSoftwareVersion() {
            Element c = id.getChild("softwareVersion");
            if (c == null) return null;
            return c.getText();
        }

        @Override
        public Map getMap() {
            return new Map(id.getChild("map"));
        }
        
        Identification(Element id) {
            this.id = id;
        }
        Element id;
    }

    @Override
    public Identification getIdentification() {
        Element id = root.getChild("identification");
        if (id == null) return null;
        return new Identification(id);
    }

    @Override
    public java.util.List<CdiRep.Segment> getSegments() {
        java.util.List list = root.getChildren("segment");
        java.util.ArrayList<CdiRep.Segment> result = new java.util.ArrayList<CdiRep.Segment>();
        for (int i = 0; i<list.size(); i++) {
            result.add(new Segment((Element)list.get(i)));
        }
        return result;
    }
    
    /**
     * Comment implementation of finding the list of contained Items
     */
    static class Nested {
        public String getName() { 
            Element d = e.getChild("name");
            if (d==null) return null;
            return d.getText();
        }
        public String getDescription() { 
            Element d = e.getChild("description");
            if (d==null) return null;
            return d.getText();
        }
        public Map getMap() {
            return new Map(e.getChild("map"));
        }
        
        public java.util.List<CdiRep.Item> getItems() {
            java.util.List<CdiRep.Item> list = new java.util.ArrayList<CdiRep.Item>();
            if (e == null) return list;
            java.util.List elements = e.getChildren();
            for (int i = 0; i<elements.size(); i++) {
                // some elements aren't contained items
                Element element = (Element)elements.get(i);
                if ("group".equals(element.getName())) list.add(new Group(element));
                else if ("bit".equals(element.getName())) list.add(new BitRep(element));
                else if ("int".equals(element.getName())) list.add(new IntRep(element));
                else if ("eventid".equals(element.getName())) list.add(new EventID(element));
                else if ("string".equals(element.getName())) list.add(new StringRep(element));
            }
            return list;
        }

        public int getIndexInParent() {
            return e.getParent().indexOf(e);
        }

        Nested(Element e) { this.e = e; }
        Element e;
    }
    
    public static class Segment extends Nested implements CdiRep.Segment {
        Segment(Element segment) { super(segment); }
        
        @Override
        public int getSpace() {
            Attribute a = e.getAttribute("space");
            try {
                if (a == null) return 0;
                else return a.getIntValue();
            } catch (org.jdom2.DataConversionException e1) { return 0; }
        }
        
        @Override
        public int getOrigin() {
            Attribute a = e.getAttribute("origin");
            try {
                if (a == null) return 0;
                else return a.getIntValue();
            } catch (org.jdom2.DataConversionException e1) { return 0; }
        }
    }

    public static class Map implements CdiRep.Map {
        Map(Element map) {
            this.map = map;
        }
        
        @Override
        public String getEntry(String key) {
            java.util.List relations = map.getChildren("relation");
            for (int i = 0; i<relations.size(); i++) {
                if (key.equals(((Element)relations.get(i)).getChild("property").getText()) )
                    return ((Element)relations.get(i)).getChild("value").getText();
            }
            return null;
        }
        
        @Override
        public String getKey(String entry) {
            java.util.List relations = map.getChildren("relation");
            for (int i = 0; i<relations.size(); i++) {
                if (entry.equals(((Element)relations.get(i)).getChild("value").getText()) )
                    return ((Element)relations.get(i)).getChild("property").getText();
            }
            return null;
        }
        
        @Override
        public java.util.List<String> getKeys() {
            java.util.ArrayList<String> list = new java.util.ArrayList<String>();
            if (map == null) return list;
            java.util.List relations = map.getChildren("relation");
            if (relations == null) return list;
            for (int i = 0; i<relations.size(); i++) {
                list.add(((Element)relations.get(i)).getChild("property").getText());
            }
            return list;
        }
        
        @Override
        public java.util.List<String> getValues() {
            java.util.ArrayList<String> list = new java.util.ArrayList<String>();
            if (map == null) return list;
            java.util.List relations = map.getChildren("relation");
            if (relations == null) return list;
            for (int i = 0; i<relations.size(); i++) {
                list.add(((Element)relations.get(i)).getChild("value").getText());
            }
            return list;
        }
        
        Element map;
    }

    public static class Item implements CdiRep.Item {
        Item(Element e) { this.e = e; }
        Element e;

        @Override
        public String getName() { 
            Element d = e.getChild("name");
            if (d==null) return null;
            return d.getText();
        }
        
        @Override
        public String getDescription() { 
            Element d = e.getChild("description");
            if (d==null) return null;
            return d.getText();
        }
        
        @Override
        public Map getMap() {
            return new Map(e.getChild("map"));
        }
        
        @Override
        public int getOffset() {
            Attribute a = e.getAttribute("offset");
            try {
                if (a == null) return 0;
                else return a.getIntValue();
            } catch (org.jdom2.DataConversionException e) { return 0; }
        }

        @Override
        public int getIndexInParent() {
            return e.getParent().indexOf(e);
        }

    }

    public static class Group extends Nested implements CdiRep.Group {
        Group(Element e) { super(e); }
        
        @Override
        public int getReplication() {
            Attribute a = e.getAttribute("replication");
            try {
                if (a == null) return 0;
                else return a.getIntValue();
            } catch (org.jdom2.DataConversionException e1) { return 0; }
        }
        
        @Override
        public int getOffset() {
            Attribute a = e.getAttribute("offset");
            try {
                if (a == null) return 0;
                else return a.getIntValue();
            } catch (org.jdom2.DataConversionException e1) { return 0; }
        }

        /**
         * Provides the name for this replication. See the CDI TN for the 
         * algorithm being used.
         * @param index a 1-based index of an element within the group
         * @return a default "Group" string if no repname elements exist
         */
        @Override
        @NonNull
        public String getRepName(int index, int replications) {
            if (index < 1) throw new IllegalArgumentException("index "+index+" must be >= 1");

            List<Element> repnames = e.getChildren("repname");
            
            // no repnames, return default
            if (repnames == null || repnames.size() == 0) return DEFAULT_REP_PREFIX;
            
            Element d;            

            // more than one repname element and index refers to not last, use the appropriate one
            if (index < repnames.size()) {  // index is 1-based as is size()
                // not last element, use the name from the repname directly
                d = repnames.get(index-1); // index is 1-based
                return d.getText();
            } 
            
            // Check for the case that this index == number of
            // repnames _and_ index == rep count.  That should not be extended.
            if (index == repnames.size() && index == replications) {
                // this the special case of _not_ extending the last repname
                d = repnames.get(index-1); // index is 1-based
                return d.getText();
            }            
            
            // in this case, we have to extend the last repname
            d = repnames.get(repnames.size()-1);
            String name = d.getText();
            
            int firstTrailingDigit = indexOfFirstTrailingDigit(name);
            if (firstTrailingDigit == -1) {
                // no final digits
                // append appropriate index taking into account prior repname elements
                int trailingNumber = index - (repnames.size()-1);
                return name+trailingNumber; // as recommended by TN, this does not add whitespace in between
            }
            
            // now we need to extract the trailing digits and index off them
            String digits = name.substring(firstTrailingDigit);
            
            int initialValue = Integer.parseInt(digits);
            int trailingNumber = initialValue + ((index-1) - (repnames.size()-1));
            return name.substring(0,firstTrailingDigit)+trailingNumber;
        }
       
        static final private String DEFAULT_REP_PREFIX = "Group";
        
        // Find the trailing digit characters, if any, in a String
        // Return the offset of the 1st digit character
        // Return -1 if not found
        int indexOfFirstTrailingDigit(String input) {
            if ( input.isEmpty() ) return -1;
            if (! Character.isDigit(input.charAt(input.length()-1)) ) return -1;
            
            // so there is at least one digit at end, scan for first non-digit
            for (int first = input.length()-1; first>=0; first--) {
                if (! Character.isDigit(input.charAt(first))) {
                    return first+1;
                }
            }
            
            // here if its all digits!
            return 0;
        }

    }

    public static class EventID extends Item implements CdiRep.EventID {
        EventID(Element e) { super(e); }
    }
    public static class IntRep extends Item implements CdiRep.IntegerRep {
        IntRep(Element e) { super(e); }
                
        @Override
        public int getDefault() { return 0; }
        @Override
        public int getMin() { return 0; }
        @Override
        public int getMax() { return 0; }

        @Override
        public int getSize() { 
            Attribute a = e.getAttribute("size");
            try {
                if (a == null) return 1;
                else return a.getIntValue();
            } catch (org.jdom2.DataConversionException e1) { return 0; }
        }
    }
    
    public static class BitRep extends Item implements CdiRep.BitRep {
        BitRep(Element e) { super(e); }
        
        @Override
        public boolean getDefault() { return false; }

        @Override
        public int getSize() { 
            Attribute a = e.getAttribute("size");
            try {
                if (a == null) return 1;
                else return a.getIntValue();
            } catch (org.jdom2.DataConversionException e1) { return 0; }
        }
    }
    
    public static class StringRep extends Item implements CdiRep.StringRep {

        StringRep(Element e) { super(e); }
        
        @Override
        public int getSize() { 
            Attribute a = e.getAttribute("size");
            try {
                if (a == null) return 1;
                else return a.getIntValue();
            } catch (org.jdom2.DataConversionException e1) { return 0; }
        }
    }

    Element root;
}
