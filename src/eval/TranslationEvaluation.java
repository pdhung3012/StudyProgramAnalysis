package eval;

import translation.Translator;

public class TranslationEvaluation {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		String fop = "C:\\Fall2017Data\\translate\\";
		//translated file
		Translator.translate(fop+"origin.jd", fop, fop+"trans.impl");
		
	}

}
