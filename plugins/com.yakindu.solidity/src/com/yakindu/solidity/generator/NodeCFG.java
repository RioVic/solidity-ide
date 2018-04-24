package com.yakindu.solidity.generator;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EObject;

public class NodeCFG {
	boolean isHideNode = false;
	boolean isFunctionDef = false;
	boolean isIFStatement = false;
	boolean isIFENDPrinted = false;
	boolean isNotLastIf = false;
	boolean isBlock = false;
	String name1;
	String name2;
	String refName;
	EObject srcObject;
	int labelNumber;
	String swimlaneCaller = UnParser.LANE_MAIN;  
	String swimlane = UnParser.LANE_MAIN;
	String pumlName;
	String level;
	List<NodeCFG> fromNodes = new ArrayList<NodeCFG>();
	List<NodeCFG> toNodes = new ArrayList<NodeCFG>();
	NodeCFG parentNode;
	Integer internalFunctionCall = null;
	List<NodeCFG> childNodes = new ArrayList<NodeCFG>();

	static NodeCFG createBlockNode() {
		NodeCFG node = new NodeCFG();
		node.isBlock = true;
		node.labelNumber = UnParser.currentLabelNumber;
		return node;
	}
	
	static NodeCFG createReferenceNode(String name1) {
		NodeCFG node = new NodeCFG();
		node.name1 = name1;
		//this.labelNumber = currentLabelNumber;
		return node;
	}

	static NodeCFG createHideNode() {
		NodeCFG node = new NodeCFG();
		node.isHideNode=true;
		node.labelNumber = UnParser.currentLabelNumber;
		return node;
	}

	private NodeCFG() {
		
	}

	NodeCFG(String name1, String name2, EObject srcObject) {
		this.name1 = name1;
		this.name2 = name2;
		this.srcObject = srcObject;
		if (srcObject != null) {
			this.labelNumber = UnParser.currentLabelNumber++;
			UnParser.maxLabelNumber = UnParser.currentLabelNumber;
		}
	}

	int getLabelNumber() {
		return labelNumber;
	}

	void addChildNode(NodeCFG n) {
		n.parentNode=this;
		childNodes.add(n);
	}

	void addFromNode(NodeCFG n) {
		if (n.isIFStatement) {
			for (NodeCFG n1 : n.toNodes) {
				fromNodes.add(n1);
				n1.toNodes.add(this);
			}
		} else {
			fromNodes.add(n);
			n.toNodes.add(this);
		}
	}

	void addToNode(NodeCFG n) {
		toNodes.add(n);
		n.fromNodes.add(this);
	}

	void printNode(String prefix, StringBuilder builder) {
		builder.append("\n" + prefix + "[");
		for (int i = 0; i < fromNodes.size(); i++) {
			if (i > 0)
				builder.append(",");
			builder.append(fromNodes.get(i).getLabelNumber());
		}
		builder.append("]-->");
		builder.append("[" + getLabelNumber() + "]\n");
		if (name2 == null)
			builder.append(prefix + getClassName(srcObject));
		else
			builder.append(prefix + getClassName(srcObject) + " " + name2);
		builder.append("\n" + prefix + name1);
	}
	
	void printRD(String previous, StringBuilder builder) {
		builder.append("\nRD [");
		for (int i = 0; i < fromNodes.size(); i++) {
			if (i > 0)
				builder.append(",");
			builder.append(fromNodes.get(i).getLabelNumber());
		}
		if (fromNodes.size() <= 1) builder.append("]-->");
		else builder.append("]-- merge control flows -->" );
		builder.append("[" + getLabelNumber() + "]");
        builder.append(previous + " " + getClassName(srcObject) + " " + name1 + "\n");
	}

	void makeRD(StringBuilder builder) {
		String entryRD = "";
		if (getLabelNumber() > 0) { 
			entryRD = UnParser.analysisRD[getLabelNumber() - 1];
			if (entryRD == null) entryRD = UnParser.analysisRD[getLabelNumber() - 2];
			UnParser.analysisRD[getLabelNumber()] = entryRD;
		}
		if (!isBlock && !isHideNode) {
			if (srcObject != null) {
				// here we check for assignment statements to kill and gen
				String nodeClass = getClassName(srcObject).substring(0, 13);
				String var = "";
				// builder.append("\n-- check for variable def and assign here: " + nodeClass);
				if (nodeClass.equals(".VariableDefi")) {
					var = name1.split(" ")[1];
					builder.append("\n-- definition of " + var);
					// in pragma 0.4.0 there is nothing to do here
				}
				if (nodeClass.equals(".AssignmentEx")) {
					var = name1.split(" ")[0];
					builder.append("\n-- assignment of " + var);
					// kill and gen RD using string functions
					int c1 = entryRD.indexOf(var) + var.length() + 1;
					int c2 = c1 + 4;
					String lab = " " + String.format("%03d", getLabelNumber());
					UnParser.analysisRD[getLabelNumber()] = entryRD.substring(0, c1) + lab + entryRD.substring(c2);
				}
				printRD(UnParser.analysisRD[getLabelNumber()], builder);
			} else
				builder.append("\n -- hide null node --");
		}
		// discussion of Solidity scoping
		// http://solidity.readthedocs.io/en/develop/control-structures.html#default-value
		if (isBlock)
			builder.append("\n-- begin block --");

		for (NodeCFG child : childNodes) {
			child.makeRD(builder);
		}

		if (isBlock)
			builder.append("\n-- end block --");
	}

	void makeAST(String prefix, StringBuilder builder) {

		if (!isBlock&&!isHideNode) {
			if (srcObject != null)
				printNode(prefix, builder);
			else
				builder.append("\n" + prefix + name1);
		}
		if (isBlock)
			builder.append("\n" + prefix + "{");

		
		for (NodeCFG child : childNodes) {
			child.makeAST(prefix + "   ", builder);
		}
		
		if (isBlock)
			builder.append("\n" + prefix + "}");
	}

	void printPuml(String prefix, StringBuilder builder) {

		if (!isBlock && pumlName != null) {

			if (isIFStatement) {
				builder.append(prefix + "#AliceBlue:" + "if ... then; \r\n");
			} else if (internalFunctionCall != null) {
				// adjust color based on contents of function
				// (known issue that there is no elegant way to handle constants in Java)
				// TODO: traverse the NodeCFG and assign levels to each label
				builder.append(prefix + UnParser.LEVEL_SFP.split(" ")[0] + pumlName + ";>\r\n");
				NodeCFG func = UnParser.tree.get(internalFunctionCall);
				if (func != null)
					func.printPuml(prefix, builder);
				builder.append(prefix + "#98D1D3:" + pumlName + ";<\r\n");
			} else if (isFunctionDef && !mayChangeState()) {
				// change to safe color
				pumlName = pumlName.replace("#LightSalmon", "#B7DDB7");
				builder.append(prefix + pumlName);
			}else
				builder.append(prefix + pumlName);

			// statements
			if (fromNodes.size() == 1)
				builder.append(prefix + "floating note left: " + fromNodes.get(0).getLabelNumber() + "\n");
			else {
				builder.append(prefix + "floating note left: ");
				int cnt = 0;
				for (NodeCFG n : fromNodes) {
					if (cnt > 0)
						builder.append(",");
					builder.append(n.getLabelNumber());
					cnt++;
				}
				builder.append("\n");
			}
			builder.append(prefix + "floating note right: " + getLabelNumber() + "\n");
			
			if (isIFStatement) {
				builder.append(prefix + pumlName);
			}
			
			if (isReturn()) {
				if (pumlName.contains(UnParser.contractName)) {
					builder.append(prefix + swimlane + prefix + UnParser.STOP);
				} else {
					builder.append(prefix + swimlaneCaller + prefix + UnParser.STOP);
					NodeCFG parent = parentNode;
					if (parent != null) {
						if (parent.isBlock)
							parent = parent.parentNode;
						if (parent.isIFStatement) {
							builder.append(prefix + "endif\n");
							parent.isIFENDPrinted=true;
						}
					}
					builder.append(prefix + UnParser.LANE_INT);
				}
			}
			builder.append("\n");
		}

		NodeCFG lastChild = null;
		for (NodeCFG child : childNodes) {
			child.printPuml(prefix + "   ", builder);
			if (child.pumlName != null)
				lastChild = child;
		}

		if (isFunctionDef) {
			if (!isLastReturn()) {
				builder.append(prefix + UnParser.LANE_EXT + 
						prefix + UnParser.STOP + 
						prefix + UnParser.LANE_INT);  
				builder.append("\n");
			}
		}
		if(isIFStatement && !isNotLastIf && !isIFENDPrinted) {
			builder.append(prefix + "endif\n");
		}
		/*
		 * if(isBlock) builder.append("\n" + prefix + "}");
		 */
	}

	private boolean mayChangeState() {
		boolean result = false;
		// Note: this is not a very good "may" analysis since we should start with the property space full
		// and look through the function body to show that all statements are safe
		if (srcObject != null) {
			// here we check for assignment statements and system operations
			String nodeClass = getClassName(srcObject).substring(0, 13);
			System.out.println(nodeClass);
			if (nodeClass.equals(".AssignmentEx") || 
					(nodeClass.equals(".OperationImp"))) 
				return true;		
		}
		System.out.println("iterate over child nodes");
		for (NodeCFG child : childNodes) {
			if(child.mayChangeState()) result = true;
		}
		return result;
	}

	boolean isReturn() {
		return "return".equals(name1) || (name1 != null && name1.startsWith("return "));
	}

	void setPumlName(String value) {
		pumlName = value;
	}
	
	boolean isLastReturn() {
		NodeCFG node = getLastChild();
		
		if(node==null)return false;
		if(node.isReturn())return true;
		
		if(node.isBlock) {
			NodeCFG n = node.getLastChild();
			if(n!=null && n.isReturn())return true;
		}
		return false;
	}
	NodeCFG getLastChild() {
		if(childNodes!=null && childNodes.size()>0)return childNodes.get(childNodes.size()-1);
		return null;
	}
	
	private String getClassName(Object object) {
		return "." + object.getClass().getSimpleName() + "@" + Integer.toHexString(object.hashCode());
	}
	
	void setLevel() {
		if ( isFunctionDef ) level = UnParser.LEVEL_SFP; // (Safe) Internal Private Functions" #98D1D3 Gravual-Green
		if (false) level = UnParser.LEVEL_PFL; // (Presume Safe) Library Functions ##BBE1C2 Gravual-LightSlateBlue	
		if (false) level = UnParser.LEVEL_NNS; // (Neutral) Non-Control Passing Statement";  // #EDEDEA Gravual-Grey 
		if (false) level = UnParser.LEVEL_NES; // (Neutral) End / Stop";  // #D94D3A Gravual-Brick*	
		if (false) level = UnParser.LEVEL_HCP; // (Highlight) Control Passing IF or LOOP Statement";   // #EFF8FE AliceBlue_puml  		
		if (false) level = UnParser.LEVEL_WFC; // (Warning) External Function Entry Read Only";  // #FCC94F Gravual-Yellow		
		if (changesState()) level = UnParser.LEVEL_DFE; // (Dangerous) External Function Entry Active";  // LightSalmon_puml			   	
	}

	private boolean changesState() {
		// TODO Auto-generated method stub
		return false;
	}


}
