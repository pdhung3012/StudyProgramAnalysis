package eval;

import java.nio.channels.AcceptPendingException;
import java.util.ArrayList;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;

import utils.FileIO;

public class ExtractSourcePathsVisitor  extends ASTVisitor {

	
	private String path;
	private String packageName;
	private String className;
	private String methodSig;
	private ASTParser parser;
	private ArrayList<String> lstSourcePath=null;
	
	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getPackageName() {
		return packageName;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public String getMethodSig() {
		return methodSig;
	}

	public void setMethodSig(String methodSig) {
		this.methodSig = methodSig;
	}

	public ASTParser getParser() {
		return parser;
	}

	public void setParser(ASTParser parser) {
		this.parser = parser;
	}
	
	public ExtractSourcePathsVisitor(String jdkLocation){
		this.path=jdkLocation;
	}

	


	
	
	
	public ArrayList<String> getLstSourcePath() {
		return lstSourcePath;
	}

	public void setLstSourcePath(ArrayList<String> lstSourcePath) {
		this.lstSourcePath = lstSourcePath;
	}

	public void prepareRelatedSourcePaths(String javaClass, String methodSig){
		this.methodSig = methodSig;
		String[] arrSourcePaths = { javaClass };
		Map options = JavaCore.getOptions();
		String strContentSource = FileIO.readStringFromFile(javaClass);
		options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
		options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM,
				JavaCore.VERSION_1_8);
		options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
		parser = ASTParser.newParser(AST.JLS8);
		parser.setCompilerOptions(options);
		parser.setEnvironment(null, new String[] {}, new String[] {}, true);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);

		parser.setSource(strContentSource.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		lstSourcePath=new ArrayList<String>();
		try {
			final CompilationUnit cu = (CompilationUnit) parser.createAST(null);
			cu.accept(this);
			parser.createASTs(arrSourcePaths, null, new String[0], null, null);
		} catch (Throwable t) {		
		}
	}
	
	@Override
	public boolean visit(CompilationUnit node) {
		packageName = node.getPackage().getName().toString();
		//node.getPackage().accept(this);
		lstSourcePath.add(node.getPackage().getName()+".*");
		for(int i=0;i<node.imports().size();i++){
			ImportDeclaration imd=(ImportDeclaration)node.imports().get(i);
			lstSourcePath.add(imd.getName().getFullyQualifiedName());
			//imd.accept(this);
		}
		//super.visit(node);
		return true;
	}
	
	@Override
	public boolean visit(ImportDeclaration node) {
		//packageName = node.getPackage().getName().toString();
		//lstSourcePath.add(node.getName().getFullyQualifiedName());
		return true;
	}
	
	@Override
	public boolean visit(PackageDeclaration node) {
		//packageName = node.getPackage().getName().toString();
		//lstSourcePath.add(node.getName().getFullyQualifiedName()+".*");
		return true;
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
