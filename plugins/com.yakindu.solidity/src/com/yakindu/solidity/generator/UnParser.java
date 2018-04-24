package com.yakindu.solidity.generator;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.generator.IFileSystemAccess2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.emf.common.util.*;
import org.eclipse.emf.ecore.*;
import com.yakindu.solidity.solidity.impl.*;
import org.yakindu.base.expressions.expressions.impl.*;

/* Yakindu Solidity object reference:
 * 
 *  TypeSpecifierImpl 
 *  ContractDefinitionImpl
 *  VariableDefinitionImpl
 *  FunctionDefinitionImpl
 *  ParameterImpl
 *  AssignmentExpressionImpl
 *  IfStatementImpl
 *  LogicalRelationExpressionImpl
 *  ReturnStatementImpl
 *  BlockImpl
 *  ElementReferenceExpressionImpl
 *  PrimitiveValueExpressionImpl
 *
 *
 */

public class UnParser {
	public static boolean isFull = false;

	public static HashMap<Integer, NodeCFG> tree = new HashMap<Integer, NodeCFG>();
	
	public static Set<String> setInternalFunction = new HashSet<String>();
	public static Set<String> setLibraryFunction = new HashSet<String>();
	public static Set<String> setTypeCast = new HashSet<String>();
	public static Set<String> setVar = new HashSet<String>();
	
	public static int currentLabelNumber;
	public static int maxLabelNumber;
	
	public static String[] analysisRD = new String[1000]; 

	public static String contractName = null;
	
	public static final String LANE_EXT = "|External Calls|\n";
	public static final String LANE_INT = "|Interfaces|\n";
	public static final String LANE_MAIN = "|Main\\n|\n";
	public static final String LANE_PRV = "|Private Functions|\n";
	public static final String LANE_LIB = "|Libraries|\n";
	public static final String STOP = "stop\n";
	
	public static final String LEVEL_SFP = "#B7DDB7: SFD (Safe) Internal Private Functions"; // Gravual-LightGreen
	public static final String LEVEL_PFL = "#98D1D3: PFL (Presume Safe) Library Functions";  // Gravual-SlateBlue*
	public static final String LEVEL_NNS = "#EDEDEA: NNS (Neutral) Non-Control Passing Statement";  // #EDEDEA Gravual-Grey 
	public static final String LEVEL_NES = "#D94D3A: NES (Neutral) End / Stop";  // #D94D3A Gravual-Brick*	
	public static final String LEVEL_HCP = "#EFF8FE: HCP (Highlight) Control Passing IF or LOOP Statement";  // #EFF8FE AliceBlue_puml  		
	public static final String LEVEL_WFC = "#FCC94F: WFC (Warning) External Function Call";  // #FCC94F Gravual-Yellow		
	public static final String LEVEL_SFE = "#B7DDB7: SFC (Safe) External Function Entry Read Only";  // // Gravual-LightGreen	
	public static final String LEVEL_WFL = "#FCC94F: WFL (Warning) State Changing Library Function Call";  // // Gravual-LightGreen	
	public static final String LEVEL_DFE = "#LightSalmon: DFE (Dangerous) External Function Entry Active"; // LightSalmon_puml	

	StringBuilder builder_ast = new StringBuilder();
	StringBuilder builder_puml = new StringBuilder();
	StringBuilder builder_src = new StringBuilder();
	StringBuilder builder_RD = new StringBuilder();
	
	private NodeCFG contractNode;
	
	public static void ast_traverse(Resource resource, IFileSystemAccess2 fsa) {
		setTypeCast.add("int(");
		setTypeCast.add("unit128(");
		setTypeCast.add("uint256(");
		setLibraryFunction.add("msg.sender.send(");	
		
		UnParser upr = new UnParser();
		upr.ast_traverse(resource, fsa, isFull);
	}

	public void ast_traverse(Resource resource, IFileSystemAccess2 fsa, boolean isDebug) {
		String uri_file = resource.getURI().toString();
		String uri_ast = uri_file + "_AST.out";
		String uri_src = uri_file + "_SRC.sol";
		String uri_puml = uri_file + "_FLW.puml";
		String uri_RD = uri_file + "_RD.out";

		builder_ast.append("Un-Parse to Expand EMF AST \n");
		builder_src.append("// Un-Parse Solidity Source Code\n");
		builder_puml.append("'Domain Specific Language Output - PUML Graphics Language - Control Flow Graph\n");
		builder_RD.append("Reaching Definitions at Each Label\n");
		try {
			BufferedReader in = new BufferedReader(
					new InputStreamReader(this.getClass().getResourceAsStream("ActivityHeader.puml")));
			String str;
			while ((str = in.readLine()) != null) {
				builder_puml.append(str + "\n");
			}
			in.close();
			builder_puml.append("\n");
		} catch (IOException e) {
			System.out.println(e.toString());
		}

		uri_file = uri_file.substring(uri_file.lastIndexOf("/"));
		if (uri_file.indexOf(".sol") != -1) {
			uri_file = uri_file.substring(0, uri_file.indexOf(".sol"));
		}

		System.out.println("<<<<<<<<<<< Start Traversal of Abstract Syntax Tree >>>>>>>>>>>>>>>>>");
		EList<EObject> list1 = resource.getContents();
		appendAST(list1);
		System.out.println(list1);
		currentLabelNumber = 0;
		NodeCFG root = new NodeCFG("root", "root", null);
		for (EObject o : list1) {
			printStateSet("setInternalFunction", setInternalFunction);
			root.addChildNode(parseObject(o, "", root));
			appendAST(o);
			System.out.println(o);
		}

		root.makeAST("", builder_ast);		
		
		if (contractNode !=null)
			contractNode.printPuml("", builder_puml);
		else
		if (root.childNodes.size() == 1)
			root.childNodes.get(0).printPuml("", builder_puml);
		else
			root.printPuml("", builder_puml);
		builder_puml.append("@enduml\n");
		
		printStateSet("IntFun: ", setInternalFunction);
		printStateSet("LibFun: ", setLibraryFunction);
		printStateSet("TypeCast: ", setTypeCast);
		printStateSet("Var: ", setVar);
		System.out.println("\n----------  Labels ---------");
		for (int lab=1; lab<=maxLabelNumber; lab++) System.out.print("Lab: " + lab);
		
		// initialize the reaching definitions from the variables
		analysisRD[1] = "";
		Iterator iterator = setVar.iterator();
		while(iterator.hasNext()){
		  String var = (String) iterator.next();
		  analysisRD[0] = analysisRD[0] + " (" + var + ", " + "001)";
		  analysisRD[1] = analysisRD[1] + " (" + var + ", " + "001)";
		}
		System.out.println("\nStartRD: " + analysisRD[1]);
		
		// traverse the nodes and update the analysis
		root.makeRD(builder_RD);
		
		fsa.generateFile(uri_ast, builder_ast.toString());
		fsa.generateFile(uri_src, builder_src.toString());
		fsa.generateFile(uri_puml, builder_puml.toString());	
		fsa.generateFile(uri_RD, builder_RD.toString());	
	}
	
	public void printStateSet(String psName, Set s) {
		System.out.println("\n----------  State Set ---------");
		Iterator iterator = s.iterator();
		while(iterator.hasNext()){
		  String element = (String) iterator.next();
		  System.out.println(psName + element);
		}
	}
	
	public void appendAST(Object o) {
		builder_ast.append(o);
	}

	public NodeCFG parseObject(EObject object, String prefix, NodeCFG linkNode) {

		int currentChildInd = 0;
		NodeCFG currentNode = null;

		if (isFull) {
			System.out.println(prefix + object.getClass());
		}

		if (object instanceof TypeSpecifierImpl) {
			TypeSpecifierImpl f = (TypeSpecifierImpl) object;
			System.out.println(prefix + "TypeSpecifierImpl:" + f.getType().getName());

		} else if (object instanceof ContractDefinitionImpl) {
			ContractDefinitionImpl con = (ContractDefinitionImpl) object;
			System.out.println(prefix + "ContractDefinitionImpl:" + con.getType().getName() + " " + con.getName());
			contractName = con.getName();
			currentNode = new NodeCFG(con.getName() + " (type: " + con.getType() + ")", null, con);
			currentNode.refName = con.getName();
			currentNode.addFromNode(linkNode);
			currentNode.setPumlName("title Ethereum " + con.getName() + ".sol\\n\n\n" + LANE_EXT
					+ LANE_INT + LANE_MAIN + LANE_PRV + "\n" + LANE_MAIN + "\n\n" + "#98D1D3:"
					+ con.getType() + " " + con.getName() + ";|\n");
			contractNode = currentNode; 

		} else if (object instanceof VariableDefinitionImpl) {
			return getVariableDefinitionImpl((VariableDefinitionImpl) object, prefix, linkNode);

		} else if (object instanceof FunctionDefinitionImpl) {
			return getFunctionDefinition((FunctionDefinitionImpl) object, prefix, linkNode);
		}else if (object instanceof BlockImpl) {
			System.out.println("BlockImpl begin");
			BlockImpl var = (BlockImpl) object;
			currentNode = NodeCFG.createBlockNode();
			currentNode.refName = "block " + var.eClass().toString();
			//currentNode.swimlane = parentNode.swimlane;
			//currentNode.swimlaneCaller = parentNode.swimlaneCaller;

		} else if (object instanceof AssignmentExpressionImpl) {
			return getAssignmentExpressionImpl((AssignmentExpressionImpl) object, prefix, linkNode);

		} else if (object instanceof IfStatementImpl) {
			return getIfStatementImpl((IfStatementImpl)object,prefix, linkNode);
			
		} else if (object instanceof ReturnStatementImpl) {
			ReturnStatementImpl ret = (ReturnStatementImpl) object;
			String name = "return";
			String exp = null;
			if (ret.getExpression() != null) {
				exp = getObjectExpression(ret.getExpression());
				name = name + " " + exp;
			}
			currentNode = new NodeCFG(name, null, object);
			currentNode.addFromNode(linkNode);
			currentNode.setPumlName(":" + name + " ;>\n");
			return currentNode;
		} else if (object instanceof ElementReferenceExpressionImpl) {// ExpressionStatementImpl
			return getElementReferenceExpression((ElementReferenceExpressionImpl)object, linkNode, null);
		}
		/*
		 * else if(object instanceof BuildInModifierImpl) { BuildInModifierImpl var =
		 * (BuildInModifierImpl)object; builder_ast.append(var.getType()+" "); }
		 */
		else if(object instanceof ExpressionStatementImpl) {
			//currentNode = NodeCFG.createHideNode();
			//linkNode = currentNode;
			if(object.eContents().size()>0)
			return parseObject(object.eContents().get(0), prefix, linkNode);
		} else if(object instanceof  FeatureCallImpl) {
			return getFeatureCallImpl((FeatureCallImpl)object,prefix, linkNode);
		}
		else {
			currentNode = new NodeCFG(getClassName(object), null, object);
			currentNode.addFromNode(linkNode);
			currentNode.setPumlName(getClassName(object));
			linkNode = currentNode;
			System.out.println(prefix + object.toString());
			String name = truncateClassName(object);
			System.out.println(prefix + object.getClass());
		}

		if (isFull)
			System.out.println(prefix + "end[" + /* labelNumber+ */"]");

		EList<EObject> list1 = object.eContents();

		// com.yakindu.solidity.solidity.impl.ContractDefinitionImpl
		// f;f.getName();f.getType()
		// NodeCFG linkNode;
/*		if (currentNode != null) {
			//parentNode.addChildNode(currentNode);
			//currentNode.swimlane = parentNode.swimlane;
			//currentNode.swimlaneCaller = parentNode.swimlaneCaller;
			if (!currentNode.isBlock) {
				linkNode = currentNode;
				// builder_puml.append(prefix + "stop\n\n");
			}
		}*/
		// else
		// linkNode = parentNode;

		if (list1 != null) {
			System.out.println(prefix + "listCount" + list1.size());
			for (int ind = currentChildInd; ind < list1.size(); ind++) {
				EObject o = list1.get(ind);
				linkNode = parseObject(o, prefix + "  ", linkNode);
				if(linkNode!=null&&currentNode!=null)
				currentNode.addChildNode(linkNode);
				/*
				 * if(currentNode!=null) parseObject(o, prefix + "  ", builder_ast,
				 * currentNode); else parseObject(o, prefix + "  ", builder_ast, parentNode);
				 */
			}
		}
		if (isFull)
			System.out.println(prefix + "endObject[" + /* labelNumber+ */"]");

		/*
		 * if (object instanceof ContractDefinitionImpl) { builder_ast.append(prefix +
		 * "\n" + prefix + "}"); } else if (object instanceof BlockImpl) {
		 * builder_ast.append(prefix + "\n" + prefix + "}"); }
		 */

		return currentNode;
	}

	/**
	 * 
	 */
	private NodeCFG getVariableDefinitionImpl(VariableDefinitionImpl var, String prefix, NodeCFG linkNode) {
		System.out.println(prefix + "VariableDefinitionImpl:" + var.getType().getName());
		// add to list of variable names
		setVar.add(var.getName());
		NodeCFG currentNode = new NodeCFG(var.getType() + " " + var.getName(),
				" (visibility: " + var.getVisibility() + ", storage: " + var.getStorage() + ")", var);
		currentNode.refName = var.getName();
		currentNode.addFromNode(linkNode);
		tree.put(var.hashCode(), currentNode);
		currentNode.setPumlName(":" + var.getType() + " " + var.getName() + ";}\n");
		if(var.eContents().size()==2) {
		System.out.println("???????????????????????????????????????????????????"+var.getName()+" "+var.eContents().size());
		System.out.println("???????????????????????????????????????????????????"+var.eContents().get(0));
		if(var.eContents().get(1) instanceof ElementReferenceExpressionImpl) {
			ElementReferenceExpressionImpl f = (ElementReferenceExpressionImpl)var.eContents().get(1);
			NodeCFG tempNode = getElementReferenceExpression((ElementReferenceExpressionImpl)var.eContents().get(1), linkNode, currentNode);
			currentNode.name1=currentNode.name1 + tempNode.name1;
			currentNode.setPumlName(":" + var.getType() + " " + var.getName() + " = "+ tempNode.name1+";}\n");
		}else {
			currentNode.setPumlName(":" + var.getType() + " " + var.getName() + " = "+  getObjectExpression(var.eContents().get(1))+";}\n");
		}
		}
		return currentNode;

	}
	
	/**
	 * Function definition
	 * @param object
	 * @return
	 */
	private NodeCFG getFunctionDefinition(FunctionDefinitionImpl func, String prefix, NodeCFG linkNode) {
		int currentChildInd = 0;
		String name = func.getName() + " (";
		int parameterCount = 0;
		int modifierCount = 0;
		boolean isPrivate = false;
		for (EObject parameter : func.eContents()) {

			if (parameter instanceof ParameterImpl) {
				ParameterImpl parameterVal = (ParameterImpl) parameter;
				if (parameterCount != 0 && modifierCount == 0) {
					name = name + ", ";
				}
				if (modifierCount != 0)
					name = name + " return (";

				parameterCount++;
				name = name + parameterVal.getType() + " ";
				if (parameterVal.getName() != null)
					name = name + parameterVal.getName();
				if (modifierCount != 0)
					name = name + " )";
				currentChildInd++;
			} else if (parameter instanceof BuildInModifierImpl) {
				if (modifierCount == 0)
					name = name + " )";
				modifierCount++;
				String mod = ((BuildInModifierImpl) parameter).getType().toString();
				if ("private".equals(mod))
					isPrivate = true;
				name = name + " " + ((BuildInModifierImpl) parameter).getType();
				currentChildInd++;
			}
		}
		NodeCFG currentNode = new NodeCFG(name, null, func);
		currentNode.isFunctionDef = true;
		currentNode.refName = func.getName();
		if (func.getName().equals(contractName)) {
			currentNode.swimlane = LANE_MAIN;
			currentNode.swimlaneCaller = LANE_MAIN;
			currentNode.setPumlName("#B7DDB7:function " + name + " " + ";<\n");
		} else if (isPrivate) {
			currentNode.swimlaneCaller = LANE_PRV;
			currentNode.swimlane = LANE_PRV;
			currentNode.setPumlName(LANE_PRV + prefix + "#98D1D3:function " + name + " " + ";<\n");
		} else {
			currentNode.swimlane = LANE_INT;
			currentNode.swimlaneCaller = LANE_EXT;				
			currentNode.setPumlName(LANE_EXT + prefix + "#FCC94F:FROM BLOCKCHAIN;|\n" + prefix
					+ LANE_INT + prefix + "#LightSalmon:function " + name + " " + ";<\n");
		}
		
		//currentNode.addFromNode(linkNode);
		NodeCFG tempFunctionDef = tree.get(func.hashCode());
		if (tempFunctionDef != null) {
			for (NodeCFG n : tempFunctionDef.fromNodes)
				currentNode.addFromNode(n);
		}
		tree.put(func.hashCode(), currentNode);
		currentNode.addFromNode(linkNode);
		
		linkNode = currentNode;
		
		for (int ind = currentChildInd; ind < func.eContents().size(); ind++) {
			EObject o = func.eContents().get(ind);
			linkNode = parseObject(o, prefix + "  ", linkNode);
			currentNode.addChildNode(linkNode);
			/*
			 * if(currentNode!=null) parseObject(o, prefix + "  ", builder_ast,
			 * currentNode); else parseObject(o, prefix + "  ", builder_ast, parentNode);
			 */
		}

		
		return currentNode;
	}
	
	/**
	 * 
	 * @param object
	 * @return
	 */
	private NodeCFG getIfStatementImpl(IfStatementImpl object, String prefix, NodeCFG linkNode) {
		NodeCFG currentNode = null;
		String logicalExpr = "(true)"; 
		int currentChildInd=0;
		
		if (object.eContents().size() > 1) {
			if (object.eContents().get(0) instanceof LogicalRelationExpressionImpl) {
				LogicalRelationExpressionImpl var = (LogicalRelationExpressionImpl) object.eContents().get(0);
				EList<EObject> list1 = var.eContents();
				String name = "";
				if (list1.size() == 2) {
					name = getObjectExpression(list1.get(0)) + " " + var.getOperator() + " " + " "
							+ getObjectExpression(list1.get(1));
					logicalExpr =name;  // here logicalExpr
				} else {
					logicalExpr = "<not included (" + getClassName(object) + ")>";
				}
				currentNode = new NodeCFG(logicalExpr, null, object);
				currentNode.addFromNode(linkNode);
				currentNode.setPumlName("if (" + logicalExpr + ") then\n\n"); 
			}else
			{
				currentNode = new NodeCFG("if (" + logicalExpr + ")", null, object);
				currentNode.addFromNode(linkNode);
				currentNode.setPumlName("if (" + getClassName(object.eContents().get(0)) + ") then\n\n"); 
			}
		}
		NodeCFG bodyNode = parseObject(object.eContents().get(1), prefix + "  ", currentNode);
		currentNode.addChildNode(bodyNode);
		if (object.eContents().size() > 2) {
			NodeCFG elseNode = new NodeCFG("else", null, null);
			NodeCFG elseNodeBody = parseObject(object.eContents().get(2), prefix + "  ", currentNode);
			if(elseNodeBody.isIFStatement) {
				currentNode.isNotLastIf=true;
				elseNodeBody.pumlName = /*"else "+*/ elseNodeBody.pumlName;
				currentNode.addChildNode(elseNodeBody);
			}else {
				elseNode.addFromNode(currentNode);
				currentNode.addChildNode(elseNode);
				//elseNode.setPumlName("else");
				elseNode.setPumlName(null);
				//currentNode.isNotLastIf=true;
				elseNode.addChildNode(elseNodeBody);
			}
		}
		currentNode.isIFStatement = true;
		//currentNode.swimlane = parentNode.swimlane;
		//currentNode.swimlaneCaller = parentNode.swimlaneCaller;
		return currentNode;

	}
	
	/**
	 * 
	 * @param object
	 * @return
	 */
	private NodeCFG getAssignmentExpressionImpl(AssignmentExpressionImpl var, String prefix, NodeCFG linkNode) {
		String name = "";
		EList<EObject> list1 = var.eContents();

		NodeCFG currentNode = new NodeCFG("name", name, var);
		if (list1.size() == 2) {
			name = getObjectExpression(list1.get(0)) + " " + var.getOperator() + " ";
			// + getObjectExpression(list1.get(1));
			if (list1.get(1) instanceof ElementReferenceExpressionImpl) {
				NodeCFG tempNode = getElementReferenceExpression((ElementReferenceExpressionImpl)list1.get(1), linkNode, currentNode);
				name = name + tempNode.name1;
			} else {
				name = name + getObjectExpression(list1.get(1));
			}
		} else {
			System.out.println(">>>>list size=" + list1.size());
			name = "<not included (" + getClassName(var) + ")>";
		}
		
		currentNode.name1 = name;
		//currentNode.swimlane = parentNode.swimlane;
		//currentNode.swimlaneCaller = parentNode.swimlaneCaller;
		// currentNode = new NodeCFG(name, name, var);
		currentNode.addFromNode(linkNode);
		if (currentNode.internalFunctionCall!=null) {//callsInternalFunction(currentNode)) { f
			// Function call needs two nodes in the graph, with the function repeated inside.
			// This is a convention of program analysis.
			currentNode.setPumlName(name);// + 
			// This is where it should go to the function, print it and return.
					/*prefix + "	"  + LANE_PRV +
					prefix + "   #98D1D3:" + name + ";<\r\n" + 
					prefix + "   		floating note left: 0, 42\r\n" + 
					prefix + "   		floating note right: 12\r\n" + 
					prefix + "        :return uint256(sha3(block.difficulty, block.coinbase, now, lastblockhashused, wager));>\r\n" +*/
			// Here it returns to the swimlane from which it was called
			/*		prefix + "  " + LANE_INT + 
					prefix + "  #B7DDB7:" + name + ";<\n\n");*/
			// Here we increment the label counter.
			currentNode.labelNumber++;

		} else {
			currentNode.setPumlName(":" + name + ";]\n");
		}
		return currentNode;
	}

	private NodeCFG getFeatureCallImpl(FeatureCallImpl feature, String prefix, NodeCFG linkNode) {
		//System.out.println("###########"+feature.getFeature());
		//System.out.println("###########"+feature.getOwner());
		String name = parseFeatureCall("",feature);
		System.out.println("###########"+feature.getArguments());
		NodeCFG n = new NodeCFG(name,null,feature);
		n.setPumlName(":"+name+";]\n");
		return n;
	}
	
	private String parseFeatureCall(String val, FeatureCallImpl feature ) {
		//if(val.length()>0)val=val+".";
		if(feature.getOwner() instanceof ElementReferenceExpressionImpl)
			val=val+"."+getObjectRef(feature.getOwner());
		System.out.println("####>>>>#######>>>>>"+feature.getOwner().getClass());
		if(feature.getOwner() instanceof FeatureCallImpl)val=parseFeatureCall(val, (FeatureCallImpl)feature.getOwner());
		
		if(feature.getFeature() instanceof org.yakindu.base.base.NamedElement) {
			return val+"."+((org.yakindu.base.base.NamedElement)feature.getFeature()).getName();
		}
		return val+"####"+feature.getOwner()+" # "+feature.getFeature();
	/*	try {
			if (object.eContents().size() > 0)
				return getEndObject(object.eContents().get(0));
			else {
				java.lang.reflect.Method method;
				method = object.getClass().getMethod("getValue");
				if (method != null)
					return "" + method.invoke(object);
				else
					return "object.getValue>>>>";
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "fff";*/
	}

	private boolean callsInternalFunction(NodeCFG currentNode) {
		Iterator iterator = setInternalFunction.iterator();
		while(iterator.hasNext()){
		  String functionName = (String) iterator.next();
		  if (currentNode.name1.contains(functionName)) return true;	 
		}
		return false;
	}

	private String getObjectExpression(EObject object) {
		if (object instanceof ElementReferenceExpressionImpl) {
			return getObjectRef(object);
		} else if (object instanceof PrimitiveValueExpressionImpl) {
			PrimitiveValueExpressionImpl varSecond = (PrimitiveValueExpressionImpl) object;
			return getEndObject(varSecond);
		}
		if (object instanceof VariableDefinitionImpl) {
			return ((VariableDefinitionImpl) object).getName();
			
		}else if(object instanceof FeatureCallImpl) {
			return parseFeatureCall("", (FeatureCallImpl)object);
		}
		else {
			return "<not included (" + getClassName(object) + ")>";
		}

		// return getClassName(object);
	}

	private String getEndObject(EObject object) {
		try {
			if (object.eContents().size() > 0)
				return getEndObject(object.eContents().get(0));
			else {
				java.lang.reflect.Method method;
				method = object.getClass().getMethod("getValue");
				if (method != null)
					return "" + method.invoke(object);
				else
					return "object.getValue>>>>";
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "fff";
	}

	private NodeCFG getElementReferenceExpression(ElementReferenceExpressionImpl varExp1, NodeCFG parentNode, NodeCFG linkNode) {

		System.out.println("ElementReferenceExpressionImpl" + varExp1.getClass());
		EObject o = varExp1.getReference();
		System.out.println("varExp1.getArguments()=" + varExp1.getArguments() + " " + varExp1.getArguments().size());

		if (o instanceof FunctionDefinitionImpl) {
			FunctionDefinitionImpl func = (FunctionDefinitionImpl) o;
			NodeCFG funcNode = tree.get(func.hashCode());
			String name = func.getName() + "(";
			setInternalFunction.add(name);
			int cnt = 0;
			for (org.yakindu.base.expressions.expressions.Argument oo : varExp1.getArguments()) {
				if (cnt > 0)
					name = name + ",";
				// System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!????????????????"+oo.getClass());
				if (oo instanceof SimpleArgumentImpl) {
					SimpleArgumentImpl a = (SimpleArgumentImpl) oo;
					// if(a.getValue() instanceof ElementReferenceExpressionImpl)
					name = name + getObjectExpression(a.getValue());// getObjectRef(a.getValue());
					// else name=name+"<<<<"+a.getValue();
				} else if (oo instanceof ElementReferenceExpressionImpl) {
					name = name + getObjectRef(oo);
					// System.out.println("??????????????????????????????????????????"+getObjectRef(oo));
				} else
					name = name + oo.getValue();
				cnt++;
			}
			name = name + ")";
			NodeCFG currentNode = null;
			if (linkNode != null) {
				currentNode = NodeCFG.createReferenceNode(name);
				linkNode.internalFunctionCall=func.hashCode();
			}
			else
				currentNode = new NodeCFG(name, null, varExp1);
			currentNode.setPumlName(name + "\n");
			currentNode.addFromNode(parentNode);
			/*if (funcNode == null) {
				funcNode = new NodeCFG(func.getName(), null, func);
				funcNode.isFunctionDef = true;
				tree.put(func.hashCode(), funcNode);
			}*/
			///////////////
	/*		NodeCFG functRefNode = new NodeCFG()
			currentNode.setPumlName("#B7DDB7:" + name + ";>\n" + 
			// This is where it should go to the function, print it and return.
					prefix + "	"  + LANE_PRV +
					prefix + "   #98D1D3:" + name + ";<\r\n" + 
					prefix + "   		floating note left: 0, 42\r\n" + 
					prefix + "   		floating note right: 12\r\n" + 
					prefix + "        :return uint256(sha3(block.difficulty, block.coinbase, now, lastblockhashused, wager));>\r\n" +
			// Here it returns to the swimlane from which it was called
					prefix + "  " + LANE_INT + 
					prefix + "  #B7DDB7:" + name + ";<\n\n");
			// Here we increment the label counter.
			currentNode.labelNumber++;*/

			//////////////
			if (linkNode == null)
				funcNode.addFromNode(currentNode);
			else
				funcNode.addFromNode(linkNode);
			return currentNode;
		} else if (o instanceof VariableDefinitionImpl) {
			return NodeCFG.createReferenceNode(((VariableDefinitionImpl) o).getName());
		}

		NodeCFG currentNode = new NodeCFG("" + getClassName(o), "<not included (" + getClassName(varExp1) + ")>", o);

		System.out.println(">>>first:" + varExp1.getReference().getClass());

		return currentNode;
	}

	private String getObjectRef(Object object) {
		System.out.println(">>>>>" + object.getClass());
		ElementReferenceExpressionImpl varExp1 = (ElementReferenceExpressionImpl) object;
		Object o = varExp1.getReference();

		if (tree.get(o.hashCode()) != null) {
			System.out.println("found node");
			return tree.get(o.hashCode()).refName;
		} else if (o instanceof VariableDefinitionImpl) {
			return ((VariableDefinitionImpl) o).getName();
		} else if (o instanceof org.yakindu.base.base.NamedElement) {
			return ((org.yakindu.base.base.NamedElement) o).getName();
		} else if (o instanceof FunctionDefinitionImpl) {
			FunctionDefinitionImpl func = (FunctionDefinitionImpl) o;
			return func.getName() + " " + getClassName(func);
		}
		System.out.println(">>>first:" + varExp1.getReference().getClass());
		return "???" + o.getClass();
	}

	private String getClassName(Object object) {
		return "." + object.getClass().getSimpleName() + "@" + Integer.toHexString(object.hashCode());
	}

	private String truncateClassName(Object object) {
		String hash = Integer.toHexString(object.hashCode());
		String var = object.toString();
		if (var != null && var.indexOf(hash) != -1) {
			String result = var.substring(var.indexOf(hash) + hash.length());
			return result;
		}
		return var;
	}
	
}
