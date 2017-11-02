package eval;

import java.util.ArrayList;

public class NodeMethodInvo {
	private ArrayList<NodeMethodInvo> children;
	private NodeMethodInvo parent;
    private String value;
    private int level;
	public ArrayList<NodeMethodInvo> getChildren() {
		return children;
	}
	public void setChildren(ArrayList<NodeMethodInvo> children) {
		this.children = children;
	}
	public NodeMethodInvo getParent() {
		return parent;
	}
	public void setParent(NodeMethodInvo parent) {
		this.parent = parent;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	public int getLevel() {
		return level;
	}
	public void setLevel(int level) {
		this.level = level;
	}
	
	public NodeMethodInvo(){
		children=new ArrayList<NodeMethodInvo>();
		level=0;
		
	}
	
	public NodeMethodInvo(NodeMethodInvo parent){
		children=new ArrayList<NodeMethodInvo>();
		this.parent=parent;		
	}
	
	public String getIndent(){
		String strLine="";
		
		int numLevel=this.getLevel();
		for(int i=0;i<numLevel;i++){
			strLine+="\t";
		}
		strLine+=value+"\n";
		for(int i=0;i<children.size();i++){
			strLine+=children.get(i).getIndent();
		}
		return strLine;
	}
    
}
