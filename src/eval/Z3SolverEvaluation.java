package eval;

public class Z3SolverEvaluation {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		String fop = "C:\\Fall2017Data\\";
		ExpressionExtractorParser eep=new ExpressionExtractorParser();
		eep.checkConsistencyMethodLevel(fop+"test.signatures.txt", fop+"test.jd", fop+"test.impl", fop+"trans.impl", fop+"z3.txt", fop+"oracle_methodLevel_3cols.txt",fop+"final_z3.txt");
		//System.out.println(""+(true||true||true||false&&false));
	}
	

}
