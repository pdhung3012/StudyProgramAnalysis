package eval;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.core.dom.NaiveASTFlattener;

import utils.FileIO;
import utils.JavaASTUtil;

public class InterProcImplVisitor extends ASTVisitor {

	int levelOfVisit = 0;
	public static int MaxLevelVisit = 5;
	public static String strErrorMsg = "ERROR_PARSE";

	private String path;
	private String[] source;
	private StringBuilder doc = new StringBuilder();
	private String packageName;
	private String className;
	private String methodSig;
	private String currentCheckMethod;
	private ASTParser parser;
	private boolean isContainThrowExTag = false;
	private NodeMethodInvo currentMethodInvo;
	private NodeMethodInvo parentMethodInvo;
	private ArrayList<String> lstSourcePath=null;
	
	

	public ArrayList<String> getLstSourcePath() {
		return lstSourcePath;
	}

	public void setLstSourcePath(ArrayList<String> lstSourcePath) {
		this.lstSourcePath = lstSourcePath;
	}

	public InterProcImplVisitor(String path, String[] source, StringBuilder sb,
			NodeMethodInvo parentNode) {
		this.path = path;
		this.source = source;
		this.doc = sb;
		this.parentMethodInvo = parentNode;

	}

	public NodeMethodInvo getCurrentMethodInvo() {
		return currentMethodInvo;
	}

	public void setCurrentMethodInvo(NodeMethodInvo currentMethodInvo) {
		this.currentMethodInvo = currentMethodInvo;
	}

	public boolean isContainThrowExTag() {
		return isContainThrowExTag;
	}

	public void setContainThrowExTag(boolean isContainThrowExTag) {
		this.isContainThrowExTag = isContainThrowExTag;
	}
	
	

	public void constructParserForFilePath(String javaClass, String methodSig) {
		// String[] sourcePaths = getSourcePaths(rootPath, new
		// String[]{".java"}, recursive);
		this.methodSig = methodSig;
		@SuppressWarnings("rawtypes")
		Map options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
		options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
		options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setCompilerOptions(options);
		parser.setEnvironment(null, new String[]{}, new String[]{}, true);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(false);
		
		HashMap<String, CompilationUnit> cus = new HashMap<>();
		FileASTRequestor r = new FileASTRequestor() {
			@Override
			public void acceptAST(String sourceFilePath, CompilationUnit ast) {
				if (ast.getPackage() == null)
					return;
				cus.put(sourceFilePath, ast);
			}
		};
		try {
			parser.createASTs(source, null, new String[0], r, null);
			for (String sourceFilePath : cus.keySet()) {
				CompilationUnit ast = cus.get(sourceFilePath);
//				String fpClassPath=path+javaClass.replaceAll("\\.", "\\\\");
				
				if(javaClass.equals(sourceFilePath)){
				//	System.out.println(javaClass+" fp "+sourceFilePath);
					ast.accept(this);
				}
			}
		} catch (Exception ex) {
			//ex.printStackTrace();
		}
		
		

		
	}

	@Override
	public boolean visit(MethodInvocation node) {
	//	System.out.println(node.toString());
		if (currentCheckMethod.equals(methodSig)) {
			NodeMethodInvo childMethodInvo = new NodeMethodInvo(
					currentMethodInvo);
			if (currentMethodInvo.getLevel() <= MaxLevelVisit) {
				childMethodInvo.setLevel(currentMethodInvo.getLevel() + 1);
				String strMethodInvo=getMethodInvoSig(node);
				childMethodInvo.setValue(strMethodInvo);
				currentMethodInvo.getChildren().add(childMethodInvo);
				String fpClassName=path+getClassName(strMethodInvo).replaceAll("\\.", "\\\\")+".java";
				ExtractSourcePathsVisitor exvChild=new ExtractSourcePathsVisitor(path);
				exvChild.prepareRelatedSourcePaths(fpClassName, strMethodInvo);
				//System.out.println("Array: "+exv.getLstSourcePath());
				String[] arrJavaPath=getSourceOfMethod(path, exvChild.getLstSourcePath());
				InterProcImplVisitor childVisitor = new InterProcImplVisitor(
						path, arrJavaPath, this.doc, childMethodInvo);
				childVisitor.constructParserForFilePath(fpClassName, strMethodInvo);
				
			}

		}
		return false;
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		className = node.getName().toString();
		// System.out.println(className+"  ssss "+node.getMethods().length);
		// node.getMethods()
		for (MethodDeclaration mi : node.getMethods()) {
			visit(mi);
		}
		return false;
	}

	@Override
	public boolean visit(Javadoc node) {

		return false;
	}

	@Override
	public boolean visit(CompilationUnit node) {
		packageName = node.getPackage().getName().toString();
		return true;
	}
	
	@Override
	public boolean visit(ImportDeclaration node) {
		//packageName = node.getPackage().getName().toString();
		return true;
	}

	@Override
	public boolean visit(MethodDeclaration node) {

		MethodDeclaration method = node;
		String methodShortName = method.getName().getIdentifier()
				+ getSigParameters(method);
		String methodName = packageName + "." + className + "."
				+ methodShortName;
		currentCheckMethod = methodName;

		if (methodSig.equals(methodName)) {
			isContainThrowExTag = false;
			parentMethodInvo.setValue(methodName);
			currentMethodInvo=parentMethodInvo;
			//System.out.println(node.getBody().toString());
			node.getBody().accept(this);;
		//	System.out.println("end body ");

		}
		//
		return true;
	}

	private String getMethodInvoSig(MethodInvocation infoNode) {
		String strResult = "";
		String className = null, methodName = null, paramInfo = null;
		
		try {
			
			IMethodBinding iMethod = infoNode.resolveMethodBinding();
			if (iMethod != null) {				
				className = iMethod.getDeclaringClass().getQualifiedName();				
				methodName = infoNode.getName().toString();
				//System.out.println("here "+className);
				
				StringBuilder sb = new StringBuilder();
				sb.append("(");
				//StringBuilder sbParams = new StringBuilder();
				int sizeArguments = iMethod.getParameterTypes().length;
				
				for (int i = 0; i < sizeArguments; i++) {
					ITypeBinding iTypeParam=iMethod.getParameterTypes()[i];
//					if(infoNode.arguments().get(i) instanceof NullLiteral){
//						NullLiteral iParamName=(NullLiteral) infoNode.arguments().get(i);
//						iTypeParam=iParamName.resolveTypeBinding();
//					} else{
//						Name iParamName = (Name) infoNode.arguments().get(i);
//						//String strParamName = iParamName.getFullyQualifiedName();
//						iTypeParam=iParamName.resolveTypeBinding();
//					}
					
					String strParamType = iTypeParam.getName();
					sb.append(strParamType);
					if (i < sizeArguments - 1) {
						sb.append(",");
					}
					
				}
				sb.append(")");
				paramInfo = sb.toString();
			}
		} catch (Exception ex) {
			//System.out.println(ex.getMessage());
			//ex.printStackTrace();
		}
		
		strResult = (className != null ? className : strErrorMsg) + "."
				+ (methodName != null ? methodName : strErrorMsg)
				+ (paramInfo != null ? paramInfo : strErrorMsg);
		return strResult;
	}
	
	private String getClassName(String strMethodSig){
		String[] arrContent=strMethodSig.split("\\(")[0].split("\\.");
		String strResult="";
		for(int i=0;i<arrContent.length-1;i++){
			strResult+=arrContent[i];
			if(i!=arrContent.length-2){
				strResult+=".";
			}
		}
		return strResult;
	}

	private String getSigParameters(MethodDeclaration method) {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		for (int i = 0; i < method.parameters().size(); i++) {
			SingleVariableDeclaration d = (SingleVariableDeclaration) (method
					.parameters().get(i));
			String type = JavaASTUtil.getSimpleType(d.getType());
			sb.append(type);
			if (i < method.parameters().size() - 1) {
				sb.append(",");
			}
		}
		sb.append(")");
		return sb.toString();
	}

	public static String getPackageInfo(String input) {
		String[] arrOutput = input.trim().split("\\.");
		String strOutput = "";
		for (int i = 0; i < arrOutput.length; i++) {
			if (!Character.isUpperCase(arrOutput[i].charAt(0))) {
				strOutput += arrOutput[i] + ".";
			} else {
				strOutput += arrOutput[i] + "AAAjava";
				break;
			}
		}
		return strOutput;
	}

	public static String[] getSourceOfMethod(String fp_location,ArrayList<String> lstPackages){
		ArrayList<String> lstResult=new ArrayList<String>();
		for(int i=0;i<lstPackages.size();i++){
			String item=lstPackages.get(i);
			if(item.endsWith(".*")){
				String sfp_java=item.replaceAll("\\.", "\\\\").replaceAll("\\*", "\\\\");
				File folder_java=new File(fp_location+sfp_java);
				if(folder_java.isDirectory()){
					File[] file_java=folder_java.listFiles();
					for(int j=0;j<file_java.length;j++){
						if(file_java[j].getAbsolutePath().endsWith(".java")){
							lstResult.add(file_java[j].getAbsolutePath());
						}
					}
				}
			}else{
				String sfp_java=item.replaceAll("\\.", "\\\\")+".java";
				File file_java=new File(fp_location+sfp_java);
				lstResult.add(file_java.getAbsolutePath());
			}
		}
		String[] arrResult=new String[lstResult.size()];
		for(int i=0;i<arrResult.length;i++){
			arrResult[i]=lstResult.get(i);
		}
		return arrResult;
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		String jdkLocation = "C:\\jdk_all_2\\";
		String fop = "C:\\Users\\pdhung\\Desktop\\hungData\\research\\ImportantProjects\\SpecMiningProject\\Fall2017\\icse-2017-priorWorks\\new_oracle\\";
		String fpOracle = fop + "oracle_v1.csv";
		String fpOnlyParamCheckResult = "C:\\Fall2017Data\\paramResult.txt";
		String strFix = "AABBAA";
		String strContent = FileIO.readStringFromFile(fpOracle);
		String[] arrContent = strContent.split("\n");
		StringBuilder sb = new StringBuilder();

		String strContainResult = "";
		for (int i = 0; i < arrContent.length; i++) {
//			 if(i!=3){
//				 continue;
//			 }
			String arrItem[] = arrContent[i].split("\t");
			String strMethodSig = arrItem[0].trim().replaceAll(strFix, ",")
					.trim();
			String className = getPackageInfo(strMethodSig).replace(".",
					File.separator).replace("AAA", ".");
			String fpClass = jdkLocation + className;
			// System.out.println(fpClass);
			NodeMethodInvo nmi = new NodeMethodInvo();
			
			//get all source paths
			ExtractSourcePathsVisitor exv=new ExtractSourcePathsVisitor(jdkLocation);
			exv.prepareRelatedSourcePaths(fpClass, strMethodSig);
			//System.out.println("Array: "+exv.getLstSourcePath());
			String[] arrJavaPath=getSourceOfMethod(jdkLocation, exv.getLstSourcePath());
			String strArrContent="";
			for(int k=0;k<arrJavaPath.length;k++){
				strArrContent+=arrJavaPath[k]+" ";
			}
			//System.out.println("Array path: "+strArrContent);
			InterProcImplVisitor visitor = new InterProcImplVisitor(
					jdkLocation, arrJavaPath, sb, nmi);
			visitor.constructParserForFilePath(fpClass, strMethodSig);
			String strCallGraph=nmi.getIndent();
			System.out.println(i + "\t" + strMethodSig + "\t"
					+ visitor.isContainThrowExTag());
			System.out.println("Call graph:\n"
					+ strCallGraph);

		}
		FileIO.writeStringToFile(strContainResult, fpOnlyParamCheckResult);
	}

}
