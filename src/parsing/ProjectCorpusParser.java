package parsing;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import clausetree.ClauseTreeNode;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;
import utils.FileIO;
import utils.JavaASTUtil;
import utils.Utils;

public class ProjectCorpusParser {
	private static final boolean PARSE_INDIVIDUAL_SRC = false, SCAN_FILES_FRIST = false, REMOVE_COMMON_WORDS = true;
	private static final String INHERIT_DOC_TAG = "@inheritDoc";
	@SuppressWarnings("serial")
	private static final HashSet<String> EXCEPTION_TAGS = new HashSet<String>(){{add("@exception"); add("@throw"); add("@throws");}};
	
	private String inPath;
	private PrintStream stLog, stClauseLocations, stClauseSource, stJavadoc; //, stLocations, stSource, stTarget;
	private boolean testing = false, buildClauseTree = false;
	private HashSet<String> badFiles = new HashSet<>();
	private HashMap<String, HashMap<String, String>> methodExceptionDocConditions = new HashMap<>();
	private HashMap<String, HashMap<String, String>> methodExceptionCodeConditions = new HashMap<>();
	private HashMap<String, String> methodLocation = new HashMap<>();
	private HashMap<String, String> overriddenMethods = new HashMap<>();
	private HashMap<String, String> getters = new HashMap<>();
	
	public ProjectCorpusParser(String inPath) {
		this.inPath = inPath;
	}
	
	public ProjectCorpusParser(String inPath, boolean testing, boolean buildClauseTree) {
		this(inPath);
		this.testing = testing;
		this.buildClauseTree = buildClauseTree;
	}

	public void generateParallelCorpus(String outPath, boolean recursive) {
		ArrayList<String> rootPaths = getRootPaths();
		
		new File(outPath).mkdirs();
		try {
			stClauseLocations = new PrintStream(new FileOutputStream(outPath + "/clause-locations.txt"));
			stClauseSource = new PrintStream(new FileOutputStream(outPath + "/clause-source.txt"));
			stJavadoc = new PrintStream(new FileOutputStream(outPath + "/javadoc.txt"));
//			stLocations = new PrintStream(new FileOutputStream(outPath + "/locations.txt"));
//			stSource = new PrintStream(new FileOutputStream(outPath + "/source.txt"));
//			stTarget = new PrintStream(new FileOutputStream(outPath + "/target.txt"));
			stLog = new PrintStream(new FileOutputStream(outPath + "/log.txt"));
		} catch (FileNotFoundException e) {
			if (testing)
				System.err.println(e.getMessage());
			return;
		}
		for (String rootPath : rootPaths) {
			String[] sourcePaths = getSourcePaths(rootPath, new String[]{".java"}, recursive);
			
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
				parser.createASTs(sourcePaths, null, new String[0], r, null);
			} catch (Throwable t) {
				t.printStackTrace(stLog);
				if (testing) {
					System.err.println(t.getMessage());
					t.printStackTrace();
					System.exit(-1);
				}
			}
			buildGetters(cus);
			for (String sourceFilePath : cus.keySet()) {
				CompilationUnit ast = cus.get(sourceFilePath);
				if (testing)
					System.out.println(sourceFilePath);
				stLog.println(sourceFilePath);
				for (int i = 0; i < ast.types().size(); i++) {
					if (ast.types().get(i) instanceof TypeDeclaration) {
						TypeDeclaration td = (TypeDeclaration) ast.types().get(i);
						generateSequence(td, sourceFilePath, ast.getPackage().getName().getFullyQualifiedName(), "");
					}
				}
			}
		}
		inlineInheritedElements();
		ArrayList<String> list = new ArrayList<>(methodExceptionCodeConditions.keySet());
		Collections.sort(list);
		StringBuilder sbSource = new StringBuilder(), sbTarget = new StringBuilder(), sbLocations = new StringBuilder();
		for (String method : list) {
			HashMap<String, String> exceptionDocConditions = methodExceptionDocConditions.get(method), exceptionCodeConditions = methodExceptionCodeConditions.get(method);
			for (String exception : exceptionCodeConditions.keySet()) {
				sbSource.append(exception + " " + exceptionDocConditions.get(exception) + "\n");
				sbTarget.append(exception + " " + exceptionCodeConditions.get(exception) + "\n");
				sbLocations.append(methodLocation.get(method) + "\n");
			}
		}
		FileIO.writeStringToFile(sbSource.toString(), outPath + "/source.txt");
		FileIO.writeStringToFile(sbTarget.toString(), outPath + "/target.txt");
		FileIO.writeStringToFile(sbLocations.toString(), outPath + "/locations.txt");
		stLog.println(new ArrayList<String>(DocumentationParser.seenTags));
	}
	
	private void buildGetters(HashMap<String, CompilationUnit> cus) {
		for (String sourceFilePath : cus.keySet()) {
			CompilationUnit ast = cus.get(sourceFilePath);
			for (int i = 0; i < ast.types().size(); i++) {
				if (ast.types().get(i) instanceof TypeDeclaration) {
					TypeDeclaration td = (TypeDeclaration) ast.types().get(i);
					buildGetters(td);
				}
			}
		}
	}

	private void buildGetters(TypeDeclaration td) {
		for (TypeDeclaration inner : td.getTypes())
			buildGetters(inner);
		for (MethodDeclaration method : td.getMethods()) {
			if (method.parameters().isEmpty() && method.getBody() != null) {
				Block body = method.getBody();
				if (body.statements().size() == 1 && body.statements().get(0) instanceof ReturnStatement) {
					ReturnStatement s  = (ReturnStatement) body.statements().get(0);
					if (s.getExpression() != null) {
						if (s.getExpression() instanceof Name) {
							Name e = (Name) s.getExpression();
							String key = JavaASTUtil.getKey(e);
							if (key != null)
								getters.put(key, method.getName().getIdentifier() + "()");
						} else if (s.getExpression() instanceof FieldAccess) {
							FieldAccess e = (FieldAccess) s.getExpression();
							if (e.getExpression() instanceof ThisExpression) {
								IVariableBinding vb = e.resolveFieldBinding();
								if (vb != null)
									getters.put(vb.getKey(), method.getName().getIdentifier() + "()");
							}
						} else if (s.getExpression() instanceof SuperFieldAccess) {
							SuperFieldAccess e = (SuperFieldAccess) s.getExpression();
							if (e.getQualifier() == null) {
								IVariableBinding vb = e.resolveFieldBinding();
								if (vb != null)
									getters.put(vb.getKey(), method.getName().getIdentifier() + "()");
							}
						}
					}
				}
			}
		}
	}

	private void inlineInheritedElements() {
		inlineInheritedDocs();
		inlineInheritedCode();
	}

	private void inlineInheritedCode() {
		// TODO Auto-generated method stub
		
	}

	private void inlineInheritedDocs() {
		HashSet<String> doneMethods = new HashSet<>();
		for (String method : new HashSet<String>(methodExceptionDocConditions.keySet())) {
			if (!doneMethods.contains(method))
				inlineInheritedDocs(method, doneMethods);
		}
	}

	private void inlineInheritedDocs(String method, HashSet<String> doneMethods) {
		if (doneMethods.contains(method))
			return;
		HashMap<String, String> exceptionDocConditions = methodExceptionDocConditions.get(method);
		if (exceptionDocConditions == null)
			return;
		for (String exception : exceptionDocConditions.keySet()) {
			String condition = exceptionDocConditions.get(exception);
			String[] conditions = condition.split(" or ");
			String inheritDoc = null;
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < conditions.length; i++) {
				String c = conditions[i];
				c = c.trim();
				if (c.equals(INHERIT_DOC_TAG)) {
					if (inheritDoc == null) {
						String overriddenMethod = overriddenMethods.get(method);
						inlineInheritedDocs(overriddenMethod, doneMethods);
						HashMap<String, String> m = methodExceptionDocConditions.get(overriddenMethod);
						if (m != null)
							inheritDoc = m.get(exception);
						if (inheritDoc == null) {
							inheritDoc = "";
							c = "";
						} else
							c = inheritDoc;
					} else
						c = "";
				}
				if (!c.isEmpty()) {
					if (i > 0)
						sb.append(" or ");
					sb.append(c);
				}
			}
			exceptionDocConditions.put(exception, sb.toString());
		}
		doneMethods.add(method);
	}

	private int generateSequence(TypeDeclaration td, String path, String packageName, String outer) {
		String[] source = new String[1];
		int numOfSequences = 0;
		String name = outer.isEmpty() ? td.getName().getIdentifier() : outer + "." + td.getName().getIdentifier();
		for (MethodDeclaration method : td.getMethods()) {
			String methodShortName = method.getName().getIdentifier() + "\t" + getParameters(method);
			String methodName = packageName + "." + name + "." + methodShortName;
			ITypeBinding tb = td.resolveBinding();
			if (tb != null)
				buildMethodHierarchy(methodName, methodShortName, tb, method);
			
			Block body = method.getBody();
//			if (body == null)
//				continue;
//			if (body.statements().isEmpty())
//				continue;
			ThrownExceptionVisitor tev = new ThrownExceptionVisitor(method);
			if (body != null)
				body.accept(tev);
//			if (tev.thrownExceptions.isEmpty())
//				continue;
			final HashMap<String, String> thrownExceptions = new HashMap<>(tev.thrownExceptions);
			for (String en : thrownExceptions.keySet()) {
				String condition = JavaASTUtil.tokenize(thrownExceptions.get(en), getters).trim();
				if (Utils.encloseClauseWithParentheses && condition.contains(" ") && !condition.startsWith("("))
					condition = "( " + condition + " )";
				thrownExceptions.put(en, condition);
			}
			stLog.println(path + "\t" + name + "\t" + method.getName().getIdentifier() + "\t" + getParameters(method));
			Javadoc doc = method.getJavadoc();
//			if (doc == null)
//				continue;
			ArrayList<String> paraNames = new ArrayList<>();
			for (int i = 0; i < method.parameters().size(); i++) {
				SingleVariableDeclaration svd = (SingleVariableDeclaration) method.parameters().get(i);
				paraNames.add(svd.getName().getIdentifier());
			}
			HashMap<String, String> docExceptionCondition = new HashMap<>();
			for (int i = 0; doc != null && i < doc.tags().size(); i++) {
				TagElement tagElement = (TagElement) doc.tags().get(i);
				if (EXCEPTION_TAGS.contains(tagElement.getTagName())) {
					if (!tagElement.fragments().isEmpty() && tagElement.fragments().get(0) instanceof Name) {
						Name exName = (Name) tagElement.fragments().get(0);
						if (exName instanceof QualifiedName)
							exName = ((QualifiedName) exName).getName();
						String exceptionName = ((SimpleName) exName).getIdentifier();
						if (exceptionName.endsWith("Exception")) {
							boolean isInheritDoc = false;
							final StringBuilder sb = new StringBuilder();
							for (int j = 1; j < tagElement.fragments().size(); j++) {
								ASTNode fragment = (ASTNode) tagElement.fragments().get(j);
								if (fragment instanceof TagElement) {
									TagElement tag = (TagElement) fragment;
									if (tag.getTagName() != null && tag.getTagName().equals(INHERIT_DOC_TAG)) {
										isInheritDoc = true;
										break;
									}
								}
								ASTNode pre = (ASTNode) tagElement.fragments().get(j-1);
								if (pre.getStartPosition() + pre.getLength() < fragment.getStartPosition())
									sb.append(" ");
								if (fragment instanceof TagElement && ExceptionDocVisitor.missingFragments((TagElement) fragment, path, source))
									j = ExceptionDocVisitor.handleEmptyTagElement((TagElement) fragment, j, tagElement, path, source, sb);
								else {
									ExceptionDocVisitor edv = new ExceptionDocVisitor(path, source, sb, getters);
									fragment.accept(edv);
								}
							}
							if (isInheritDoc) {
								String condition = docExceptionCondition.get(exceptionName);
								if (condition == null)
									condition = INHERIT_DOC_TAG;
								else 
									condition += " or " + INHERIT_DOC_TAG;
								docExceptionCondition.put(exceptionName, condition);
							} else {
								System.out.println(sb.toString());
								if (buildClauseTree) {
									HashMap<String, String> codeStr = new HashMap<>(), strCode = new HashMap<>();
									String docSequence = DocumentationParser.normalize(sb.toString(), codeStr, strCode, paraNames);
									List<CoreMap> sentences = NLPSentenceParser.parse(docSequence);
									for (CoreMap sentence : sentences) {
										numOfSequences++;
										System.out.println(sentence);
										Tree tree = sentence.get(TreeAnnotation.class);
										System.out.println(tree);
										NLPSentenceParser.print(tree);
										ClauseTreeNode clauseTree = NLPSentenceParser.buildClauseTree(tree, new Stack<>(), new Stack<>(), new HashSet<>(), strCode);
										clauseTree.print();
										String flattenedSequence = clauseTree.flatten();
										if (!flattenedSequence.equals("null")) {
											if (REMOVE_COMMON_WORDS) {
												flattenedSequence = flattenedSequence.replace(" be ", " ");
												flattenedSequence = flattenedSequence.replace("the ", "");
												flattenedSequence = flattenedSequence.replace(" specify ", " ");
											}
											String condition = docExceptionCondition.get(exceptionName);
											if (condition == null)
												condition = flattenedSequence;
											else 
												condition += " or " + flattenedSequence;
											docExceptionCondition.put(exceptionName, condition);
											stClauseLocations.print(path + "\t" + packageName + "\t" + name + "\t" + method.getName().getIdentifier() + "\t" + getParameters(method) + "\t" + sentences.size() + "\n");
											stClauseSource.print(exceptionName + " " + flattenedSequence + "\n");
											stJavadoc.print(sb.toString() + "\n");
										}
									}
								} else {
									String flattenedSequence = sb.toString();
									if (!flattenedSequence.isEmpty()) {
										String condition = docExceptionCondition.get(exceptionName);
										if (condition == null)
											condition = flattenedSequence;
										else 
											condition += " or " + flattenedSequence;
										docExceptionCondition.put(exceptionName, condition);
										stClauseLocations.print(path + "\t" + packageName + "\t" + name + "\t" + method.getName().getIdentifier() + "\t" + getParameters(method) + "\t" + 1 + "\n");
										stClauseSource.print(exceptionName + " " + flattenedSequence + "\n");
										stJavadoc.print(sb.toString() + "\n");
									}
								}
							}
						}
					}
				}
			}
			/*if (!docExceptionCondition.isEmpty())*/ {
				methodExceptionCodeConditions.put(methodName, thrownExceptions);
				methodExceptionDocConditions.put(methodName, docExceptionCondition);
				methodLocation.put(methodName, path + "\t" + packageName + "\t" + name + "\t" + method.getName().getIdentifier() + "\t" + getParameters(method) + "\t" + docExceptionCondition.size() + "\t" + thrownExceptions.size());
			}
		}
		for (TypeDeclaration inner : td.getTypes())
			numOfSequences += generateSequence(inner, path, packageName, name);
		return numOfSequences;
	}

	private void buildMethodHierarchy(String methodName, String methodShortName, ITypeBinding tb, MethodDeclaration method) {
		if (tb.getSuperclass() != null) {
			ITypeBinding stb = tb.getSuperclass().getTypeDeclaration();
			for (IMethodBinding mb : stb.getDeclaredMethods()) {
				if (method.resolveBinding() != null && method.resolveBinding().overrides(mb)) {
					String name = mb.getDeclaringClass().getQualifiedName() + "." + methodShortName;
					overriddenMethods.put(methodName, name);
					return;
				}
			}
			buildMethodHierarchy(methodName, methodShortName, stb, method);
			if (this.overriddenMethods.containsKey(methodName))
				return;
		}
		for (ITypeBinding itb : tb.getInterfaces()) {
			for (IMethodBinding mb : itb.getTypeDeclaration().getDeclaredMethods()) {
				if (method.resolveBinding() != null && method.resolveBinding().overrides(mb)) {
					String name = mb.getDeclaringClass().getQualifiedName() + "." + methodShortName;
					overriddenMethods.put(methodName, name);
					return;
				}
			}
			buildMethodHierarchy(methodName, methodShortName, itb.getTypeDeclaration(), method);
			if (this.overriddenMethods.containsKey(methodName))
				return;
		}
	}
	
	private String getParameters(MethodDeclaration method) {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		for (int i = 0; i < method.parameters().size(); i++) {
			SingleVariableDeclaration d = (SingleVariableDeclaration) (method.parameters().get(i));
			String type = JavaASTUtil.getSimpleType(d.getType());
			sb.append("\t" + type);
		}
		sb.append("\t)");
		return sb.toString();
	}

	private String[] getSourcePaths(String path, String[] extensions, boolean recursive) {
		HashSet<String> exts = new HashSet<>();
		for (String e : extensions)
			exts.add(e);
		HashSet<String> paths = new HashSet<>();
		getSourcePaths(new File(path), paths, exts, recursive);
		paths.removeAll(badFiles);
		return (String[]) paths.toArray(new String[0]);
	}

	private void getSourcePaths(File file, HashSet<String> paths, HashSet<String> exts, boolean recursive) {
		if (file.isDirectory()) {
			if (paths.isEmpty() || recursive)
				for (File sub : file.listFiles())
					getSourcePaths(sub, paths, exts, recursive);
		} else if (exts.contains(getExtension(file.getName())))
			paths.add(file.getAbsolutePath());
	}

	private Object getExtension(String name) {
		int index = name.lastIndexOf('.');
		if (index < 0)
			index = 0;
		return name.substring(index);
	}

	private ArrayList<String> getRootPaths() {
		ArrayList<String> rootPaths = new ArrayList<>();
		if (PARSE_INDIVIDUAL_SRC)
			getRootPaths(new File(inPath), rootPaths);
		else {
			if (SCAN_FILES_FRIST)
				getRootPaths(new File(inPath), rootPaths);
			rootPaths = new ArrayList<>();
			rootPaths.add(inPath);
		}
		return rootPaths;
	}

	private void getRootPaths(File file, ArrayList<String> rootPaths) {
		if (file.isDirectory()) {
			System.out.println(rootPaths);
			for (File sub : file.listFiles())
				getRootPaths(sub, rootPaths);
		} else if (file.getName().endsWith(".java")) {
			@SuppressWarnings("rawtypes")
			Map options = JavaCore.getOptions();
			options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
			options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
			options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
			ASTParser parser = ASTParser.newParser(AST.JLS8);
			parser.setCompilerOptions(options);
			parser.setSource(FileIO.readStringFromFile(file.getAbsolutePath()).toCharArray());
			try {
				CompilationUnit ast = (CompilationUnit) parser.createAST(null);
				if (ast.getPackage() != null && !ast.types().isEmpty() && ast.types().get(0) instanceof TypeDeclaration) {
					String name = ast.getPackage().getName().getFullyQualifiedName();
					name = name.replace('.', '\\');
					String p = file.getParentFile().getAbsolutePath();
					if (p.endsWith(name))
						add(p.substring(0, p.length() - name.length() - 1), rootPaths);
				} /*else 
					badFiles.add(file.getAbsolutePath());*/
			} catch (Throwable t) {
				badFiles.add(file.getAbsolutePath());
			}
		}
	}

	private void add(String path, ArrayList<String> rootPaths) {
		int index = Collections.binarySearch(rootPaths, path);
		if (index < 0) {
			index = - index - 1;
			int i = rootPaths.size() - 1;
			while (i > index) {
				if (rootPaths.get(i).startsWith(path))
					rootPaths.remove(i);
				i--;
			}
			i = index - 1;
			while (i >= 0) {
				if (path.startsWith(rootPaths.get(i)))
					return;
				i--;
			}
			rootPaths.add(index, path);
		}
	}

}
