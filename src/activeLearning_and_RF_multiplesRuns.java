/*
 * Bonan : Change history:
 * - 2011-05-05 add LevDis fuzzyMatch
 * 
 * type1: add random seed

 * type2: add seed with maximum dim (with 10 tops)
 * type3: add seed with maximum dim (with 10 tops), and use negative feedbacks (richicoo)
 */


import java.io.*;


import java.util.*;
import java.lang.*;

//import DQE.Ne_candidate;
//import DQE.Weight;

public class activeLearning_and_RF_multiplesRuns {

	public static String file_goldenset;
	public static double thres_candidate_pool = 0.04;
	public static HashSet<Integer> candidate_pool;


	public static enum Corpus_type {WIKI, NEWS}
	//	public final static Corpus_type corpus_type = Corpus_type.WIKI;
	public final static Corpus_type corpus_type = Corpus_type.NEWS;

	public static enum Query_type {Interactive, Dict_lookup}
	public final static Query_type query_type = Query_type.Interactive;

	public static double beta = 0.75;
	public static double gama = -0.25;


	// Expo: exponentially
	// Richhcco: linear (almost)
	public static enum Type3_adjust_type {Expo, Richcco}
	//	public final static Corpus_type corpus_type = Corpus_type.WIKI;
	public final static Type3_adjust_type type3_adjust_type = Type3_adjust_type.Richcco;


	public static int max_seeds;
	public static int max_round = 10;

	//	public static double weight_gain = 0.5;
	//	public static double thres_sim = 0.005;
	public static double weight_gain;
	public static double thres_sim;
	public static double weight_discount;

	public static Random generator;

	public static HashMap<Integer, HashMap<Integer, Double> > ne_feature_logpmi;
	public static HashMap<Integer, String> index_ne;
	public static HashMap<Integer, String> index_feature;
	public static HashSet<String> stop_words;

	//	public static HashSet<Integer> feature_all;
	public static HashSet<Integer> feature1;
	public static HashSet<Integer> feature2;

	public static HashSet<Integer> goldenset;
	//	public static ArrayList<Integer> seeds;

	// 3 different seed set, seeds[3]
	public static int num_of_methods = 6;
	public static ArrayList<Integer>[] seeds;

	//	public static ArrayList<Integer> set_wrong;

	// 3 different set_wrong, set_wrong[3]
	public static ArrayList<Integer>[] set_wrong;	

	// for testing purpose only
	public static ArrayList<Weight> test_rank_features;

	public static void load_stop_words(String filename) {
		stop_words = new HashSet();

		try {
			FileInputStream fstream = new FileInputStream(filename);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			String str_line;
			while((str_line = br.readLine())!=null) {
				if(str_line.charAt(0) == '#')
					continue;
				stop_words.add(str_line.toLowerCase());
			}

			in.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void load_goldenset_seeds(String filename) {
		goldenset = new HashSet();

		// seeds = (ArrayList<Integer>[])new ArrayList[num_of_methods];
		// set_wrong = (ArrayList<Integer>[])new ArrayList[num_of_methods];

		seeds = new ArrayList[num_of_methods];
		set_wrong = new ArrayList[num_of_methods];
		for(int i=0; i<num_of_methods; i++) {
			seeds[i] = new ArrayList<Integer>();
			set_wrong[i] = new ArrayList<Integer>();
		}

		try {
			FileInputStream fstream = new FileInputStream(filename);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			String str_line;
			while((str_line = br.readLine())!=null) {
				String [] items = str_line.split("\t");
				int index = Integer.parseInt(items[0]);

				goldenset.add(index);
			}

			// random select seeds;
			for(int i=0; i < max_seeds; i++) {
				int randomIndex = generator.nextInt(100000) % goldenset.size();

				int ne_index = 0;
				int offset = 0;
				Iterator<Integer> it = goldenset.iterator();
				while(it.hasNext()) {
					ne_index = it.next();
					if(offset == randomIndex)
						break;

					offset++;
				}

				for(int imtds=0; imtds<num_of_methods; imtds++)
					seeds[imtds].add(ne_index);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void load_index_ne(String filename) {
		index_ne = new HashMap();

		try {
			FileInputStream fstream = new FileInputStream(filename);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			String str_line;
			while((str_line = br.readLine())!=null) {
				String [] items = str_line.split("\t");
				String ne = items[0];
				int index = Integer.parseInt(items[1]);

				index_ne.put(index, ne);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void load_idx_feature(String filename) {
		index_feature = new HashMap();

		try {
			FileInputStream fstream = new FileInputStream(filename);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			String str_line;
			while((str_line = br.readLine())!=null) {
				String [] items = str_line.split("\t");
				String feature = items[0];
				int index = Integer.parseInt(items[1]);

				index_feature.put(index, feature);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static boolean containsOnlyLetters(String str) {
		//It can't contain only numbers if it's null or empty...
		if (str == null || str.length() == 0)
			return false;

		for (int i = 0; i < str.length(); i++) {

			//If we find a non-digit character we return false.
			if (!Character.isLetter(str.charAt(i)))
				return false;
		}

		return true;
	}

	public static void load_ne_feature_logpmi(String filename) throws InterruptedException {
		ne_feature_logpmi = new HashMap();

		//		feature_all = new HashSet();
		feature1 = new HashSet();
		feature2 = new HashSet();

		try {
			FileInputStream fstream = new FileInputStream(filename);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			String str_line;
			while((str_line = br.readLine())!=null) {
				String [] items = str_line.split("\t");
				int iNE = Integer.parseInt(items[0]);
				int iFeature = Integer.parseInt(items[1]);

				if(!index_feature.containsKey(iFeature))
					continue;

				String str_feature = index_feature.get(iFeature);

				/* only for news, filter not-so-interesting (noisy) features */
				if(corpus_type == Corpus_type.NEWS) {		
					String [] str_feature_items = str_feature.split("_");
					if(str_feature_items.length<3)
						continue;

					String str_token = str_feature_items[str_feature_items.length-1];

					if( !containsOnlyLetters(str_token) ||
							stop_words.contains(str_token) ||
							str_feature.contains("det_") ||
							str_feature.contains("num_"))
						continue;
					/*	
					// for syntacticTokenOnly features
					if(str_feature.length() == 1 ||
							!containsOnlyLetters(str_feature)||
							stop_words.contains(str_feature))
						continue;
					 */
				}

				if(items[2]=="NaN")
					continue;
				// 

				double logpmi = Double.parseDouble(items[2]);

				/*
				String test_line = String.format("DBG: %d, %d, %f", iNE, iFeature, logpmi);
				System.out.println(test_line);
				Thread.sleep(100);
				 */

				if(!ne_feature_logpmi.containsKey(iNE)) {
					HashMap<Integer, Double> hm = new HashMap();
					hm.put(iFeature, logpmi);
					ne_feature_logpmi.put(iNE, hm);
				}
				else {
					if(!ne_feature_logpmi.get(iNE).containsKey(iFeature)) {
						ne_feature_logpmi.get(iNE).put(iFeature, logpmi);
					}
				}

				if(!feature1.contains(iFeature) && !feature2.contains(iFeature)) {

					int randomIndex = generator.nextInt(1000) % 2;
					if(randomIndex == 0)
						feature1.add(iFeature);
					else
						feature2.add(iFeature);
				}
				//				feature_all.add(iFeature);
			}
			/*
			// random split features, but ensure the two centroid is not empty;
			while(true) {
				Iterator<Integer> itf = feature_all.iterator();
				while(itf.hasNext())
				{
					int iFeature = itf.next();
					if(!feature1.contains(iFeature) && !feature2.contains(iFeature)) {

						int randomIndex = generator.nextInt(1000) % 2;
						if(randomIndex == 0)
							feature1.add(iFeature);
						else
							feature2.add(iFeature);
					}
				}

				Boolean isvalid_cent1 = false, isvalid_cent2 = false; 
				Iterator<Integer> it = seeds.iterator();
				while(it.hasNext()) {
					int iSeed = it.next();

					Iterator<Integer> it2 = ne_feature_logpmi.get(iSeed).keySet().iterator();
					while(it2.hasNext()) {
						int iFeature = it2.next();
						double logpmi = ne_feature_logpmi.get(iSeed).get(iFeature);

						if(logpmi!=0) {
							if(feature1.contains(iFeature))
								isvalid_cent1 = true;
							if(feature2.contains(iFeature))
								isvalid_cent2 = true;
						}
					}
				}

				if(isvalid_cent1 == true && isvalid_cent2 == true)
					break;
			}
			 */
			System.out.print("Feature1.size(): ");
			System.out.println(feature1.size());
			System.out.print("Feature2.size(): ");
			System.out.println(feature2.size());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// id_classifier = 1, 1st half view
	// id_classifier = 2, 2nd half view
	// id_classifier = 3, entire feature space
	public static double cal_simularity(HashMap<Integer, Double> v1, HashMap<Integer, Double> v2, 
			double sqrt_v1, double sqrt_v2,
			int id_classifier, HashMap<Integer, Double> weight) {
		// for testing purpose
		test_rank_features.clear();

		if(sqrt_v1 == 0 || sqrt_v2 == 0 || v1.isEmpty() || v2.isEmpty())
			return 0;

		double v1v2 = 0;
		if(v1.size() < v2.size()) {
			Iterator<Integer> it = v1.keySet().iterator();
			while(it.hasNext()) {
				int iFeature = it.next();
				if(v2.containsKey(iFeature)) {
					if(id_classifier == 1 && !feature1.contains(iFeature))
						continue;
					if(id_classifier == 2 && !feature2.contains(iFeature))
						continue;

					// weight features;
					double w;
					if(weight.containsKey(iFeature))
						w = weight.get(iFeature);
					else
						w = 1;

					double d_a = w * v1.get(iFeature);
					double d_b = w * v2.get(iFeature);
					double d_c = d_a * d_b;
					if(!Double.isNaN(d_c)) {
						v1v2 += d_c;

						Weight we = new Weight();
						we.iFeature = iFeature;
						we.w = d_c;
						test_rank_features.add(we);
					}
				}
			}
		}
		else {
			Iterator<Integer> it = v2.keySet().iterator();
			while(it.hasNext()) {
				int iFeature = it.next();
				if(v1.containsKey(iFeature)) {
					if(id_classifier == 1 && !feature1.contains(iFeature))
						continue;
					if(id_classifier == 2 && !feature2.contains(iFeature))
						continue;

					// weight feature
					double w;
					if(weight.containsKey(iFeature))
						w = weight.get(iFeature);
					else
						w = 1;

					double d_a = w * v1.get(iFeature);
					double d_b = w * v2.get(iFeature);
					double d_c = d_a * d_b;
					if(!Double.isNaN(d_c)) {
						v1v2 += d_c;

						Weight we = new Weight();
						we.iFeature = iFeature;
						we.w = d_c;
						test_rank_features.add(we);
					}
				}
			}
		}

		return v1v2 / (sqrt_v1 * sqrt_v2);
	}

	// get precision from two views
	public static HashMap<Integer, Double> get_precision_2view(ArrayList<Ne_candidate> vec_name1,
			ArrayList<Ne_candidate> vec_name2, String out_file, int round, int exper_no) throws IOException {

		String f_out = String.format("%s.%d.%d.rank", out_file, round, exper_no);
		BufferedWriter out_list = new BufferedWriter(new FileWriter(f_out));

		HashMap<Integer, Double> map_p = new HashMap<Integer, Double>();

		ArrayList<Ne_candidate> words_sim = new ArrayList<Ne_candidate>();

		// to speed up, use 20000 instead of words_sim.size()
		int max_index = 20000;
		if(vec_name1.size() < max_index)
			max_index = vec_name1.size();
		if(vec_name2.size() < max_index)
			max_index = vec_name2.size();

		for(int cc = 0; cc < max_index; cc++) {
			Ne_candidate f = new Ne_candidate();

			double sim1 = 0, sim2 = 0;
			sim1 = vec_name1.get(cc).sim;

			for(int cc2 = 0; cc2 < max_index; cc2++) {
				if(vec_name2.get(cc2).iNE == f.iNE) {
					sim2 = vec_name2.get(cc2).sim;
					break;
				}
			}

			f.iNE = vec_name1.get(cc).iNE;
			f.sim = 1-(1-sim1)*(1-sim2);

			words_sim.add(f);
		}

		Collections.sort(words_sim, Collections.reverseOrder());

		for(int i=1; i <= seeds[exper_no-1].size(); i++)
			map_p.put(i, new Double(1.0));

		int valid = seeds[exper_no-1].size();
		// 5 times size of golden set, to find unincluded items in golden set
		for(int i=seeds[exper_no-1].size()+1; i <= words_sim.size(); i++) {
//		for(int i=seeds[exper_no-1].size()+1; i <= goldenset.size() * 5; i++) {
			int is_valid = 0;
			int cur_iNE = words_sim.get(i-seeds[exper_no-1].size()-1).iNE;
			if(goldenset.contains(cur_iNE)) {
				valid++;
				is_valid = 1;
			}
			String str_test = String.format("%d\t%d\t%f\t%s\t%d", i, is_valid, words_sim.get(i-seeds[exper_no-1].size()-1).sim, index_ne.get(cur_iNE), cur_iNE);
			out_list.write(str_test);
			for(int j = 0; j < words_sim.get(i-seeds[exper_no-1].size()-1).top_features.size(); j++) {
				out_list.write("\t");
				int cur_iFeature = words_sim.get(i-seeds[exper_no-1].size()-1).top_features.get(j).iFeature;
				out_list.write(index_feature.get(cur_iFeature));
			}
			out_list.write("\n");

			map_p.put(i, new Double(1.0*valid/i));
		}

		out_list.close();
		return map_p;
	}

	// get precision from a single view (entire feature space)
	public static HashMap<Integer, Double> get_precision_singleView(ArrayList<Ne_candidate> vec_name,
			String out_file, int round, int exper_no) throws IOException {

		String f_out = String.format("%s.%d.%d.rank", out_file, round, exper_no);
		BufferedWriter out_list = new BufferedWriter(new FileWriter(f_out));

		HashMap<Integer, Double> map_p = new HashMap<Integer, Double>();

		ArrayList<Ne_candidate> words_sim = new ArrayList<Ne_candidate>(vec_name);


		Collections.sort(words_sim, Collections.reverseOrder());

		for(int i=1; i <= seeds[exper_no-1].size(); i++)
			map_p.put(i, new Double(1.0));

		int valid = seeds[exper_no-1].size();
		// 5 times size of golden set, to find unincluded items in golden set
		for(int i=seeds[exper_no-1].size()+1; i <= goldenset.size() * 5; i++) {
			int is_valid = 0;
			int cur_iNE = words_sim.get(i-seeds[exper_no-1].size()-1).iNE;
			if(goldenset.contains(cur_iNE)) {
				valid++;
				is_valid = 1;
			}
			String str_test = String.format("%d\t%d\t%f\t%s\t%d", i, is_valid, words_sim.get(i-seeds[exper_no-1].size()-1).sim, index_ne.get(cur_iNE), cur_iNE);
			out_list.write(str_test);
			for(int j = 0; j < words_sim.get(i-seeds[exper_no-1].size()-1).top_features.size(); j++) {
				out_list.write("\t");
				int cur_iFeature = words_sim.get(i-seeds[exper_no-1].size()-1).top_features.get(j).iFeature;
				out_list.write(index_feature.get(cur_iFeature));
			}
			out_list.write("\n");

			map_p.put(i, new Double(1.0*valid/i));
		}

		out_list.close();
		return map_p;
	}

	// return value:
	// HashMap<Integer, Double> centroid
	public static HashMap<Integer, Double> get_centroid(ArrayList<Integer> seeds, int id_classifier) {
		HashMap<Integer, Double> centroid = new HashMap();
		Iterator<Integer> it = seeds.iterator();
		while(it.hasNext()) {
			int iSeed = it.next();

			Iterator<Integer> it2 = ne_feature_logpmi.get(iSeed).keySet().iterator();
			while(it2.hasNext()) {
				int iFeature = it2.next();
				// ? bug to fix / TODO: 
				double logpmi = ne_feature_logpmi.get(iSeed).get(iFeature)/seeds.size();

				if(id_classifier==1 && !feature1.contains(iFeature))
					continue;
				if(id_classifier==2 && !feature2.contains(iFeature))
					continue;

				if(!centroid.containsKey(iFeature))
					centroid.put(iFeature, logpmi);
				else {
					double old_value = centroid.get(iFeature);
					centroid.put(iFeature, old_value + logpmi);
				}
			}
		}

		return centroid;
	}


	// calculate centroid based on richicco formula
	public static HashMap<Integer, Double> get_centroid_richicoo(ArrayList<Integer> seeds, int id_classifier, ArrayList<Integer> set_wrong) {
		// parameters in Richico formula
		Set<Integer> validfeatureIdx_all = new HashSet<Integer>();

		HashMap<Integer, Double> centroid = new HashMap();

		// centroid for the initial set of seeds
		HashMap<Integer, Double> centroid_raw = new HashMap();
		int nseeds = 0;
		Iterator<Integer> it_raw = seeds.iterator();
		while(it_raw.hasNext()) {
			//			nseeds++;
			//			if(nseeds > max_seeds)
			//				continue;

			int iSeed = it_raw.next();

			Iterator<Integer> it2 = ne_feature_logpmi.get(iSeed).keySet().iterator();
			while(it2.hasNext()) {
				int iFeature = it2.next();

				if(!validfeatureIdx_all.contains(iFeature))
					validfeatureIdx_all.add(iFeature);

				// normalize
				double logpmi = ne_feature_logpmi.get(iSeed).get(iFeature)/seeds.size();

				if(id_classifier==1 && !feature1.contains(iFeature))
					continue;
				if(id_classifier==2 && !feature2.contains(iFeature))
					continue;

				if(!centroid_raw.containsKey(iFeature))
					centroid_raw.put(iFeature, logpmi);
				else {
					double old_value = centroid_raw.get(iFeature);
					centroid_raw.put(iFeature, old_value + logpmi);
				}
			}
		}

		// centroid for pos feedbacks
		HashMap<Integer, Double> centroid_pos = new HashMap();
		/*
		nseeds = 0;
		Iterator<Integer> it_pos = seeds.iterator();
		while(it_pos.hasNext()) {
			nseeds++;
			if(nseeds <= max_seeds)
				continue;

			int iSeed = it_pos.next();

			Iterator<Integer> it2 = ne_feature_logpmi.get(iSeed).keySet().iterator();
			while(it2.hasNext()) {
				int iFeature = it2.next();

				if(!validfeatureIdx_all.contains(iFeature))
					validfeatureIdx_all.add(iFeature);

				// normalize
				double logpmi = ne_feature_logpmi.get(iSeed).get(iFeature)*beta/(seeds.size() - max_seeds);

				if(id_classifier==1 && !feature1.contains(iFeature))
					continue;
				if(id_classifier==2 && !feature2.contains(iFeature))
					continue;

				if(!centroid_pos.containsKey(iFeature))
					centroid_pos.put(iFeature, logpmi);
				else {
					double old_value = centroid_pos.get(iFeature);
					centroid_pos.put(iFeature, old_value + logpmi);
				}
			}
		}
		 */
		// centroid for neg feedbacks
		HashMap<Integer, Double> centroid_neg = new HashMap();
		Iterator<Integer> it_neg = set_wrong.iterator();
		while(it_neg.hasNext()) {
			int iSeed = it_neg.next();

			Iterator<Integer> it2 = ne_feature_logpmi.get(iSeed).keySet().iterator();
			while(it2.hasNext()) {
				int iFeature = it2.next();

				if(!validfeatureIdx_all.contains(iFeature))
					validfeatureIdx_all.add(iFeature);

				// normalize
				double logpmi = ne_feature_logpmi.get(iSeed).get(iFeature)*gama/set_wrong.size();

				if(id_classifier==1 && !feature1.contains(iFeature))
					continue;
				if(id_classifier==2 && !feature2.contains(iFeature))
					continue;

				if(!centroid_neg.containsKey(iFeature))
					centroid_neg.put(iFeature, logpmi);
				else {
					double old_value = centroid_neg.get(iFeature);
					centroid_neg.put(iFeature, old_value + logpmi);
				}
			}
		}

		// combine all features
		for(Integer idx_feature : validfeatureIdx_all) {
			double logpmi = 0;

			if(centroid_raw.containsKey(idx_feature))
				logpmi += centroid_raw.get(idx_feature);

			//			if(centroid_pos.containsKey(idx_feature))
			//				logpmi += centroid_pos.get(idx_feature);

			if(centroid_neg.containsKey(idx_feature))
				logpmi += centroid_neg.get(idx_feature);

			// no Negative weighted items are added

			if(Double.isNaN(logpmi))
				continue;			
			if(logpmi <= 0)
				continue;

			centroid.put(idx_feature, logpmi);
		}

		return centroid;
	}



	// return value:
	// HashMap<Integer, Double> weight
	public static HashMap<Integer, Double> increase_weight_type3(ArrayList<Integer> seeds, int id_classifier, HashMap<Integer, Double> weight) {
		Iterator<Integer> it = seeds.iterator();
		int nseeds = 0;
		while(it.hasNext()) {
			nseeds++;

			// for the initial seed set, don't adjust weight
			if(nseeds <= max_seeds)
				continue;

			int iSeed = it.next();

			Iterator<Integer> it2 = ne_feature_logpmi.get(iSeed).keySet().iterator();
			while(it2.hasNext()) {
				int iFeature = it2.next();

				if(id_classifier==1 && !feature1.contains(iFeature))
					continue;
				if(id_classifier==2 && !feature2.contains(iFeature))
					continue;

				if(!weight.containsKey(iFeature))
					weight.put(iFeature, new Double(1 * weight_gain));
				else {
					double old_value = weight.get(iFeature);
					old_value *= weight_gain;
					weight.put(iFeature, old_value);
				}
			}
		}

		return weight;

	}

	/*
	// return value:
	// HashMap<Integer, Double> weight
	public static HashMap<Integer, Double> setZero_weight_type2(ArrayList<Integer> set_wrong, int id_classifier, HashMap<Integer, Double> weight) {
		Iterator<Integer> it22 = set_wrong.iterator();
		while(it22.hasNext()) {
			int name_wrong = it22.next();
			Iterator<Integer> it11 = ne_feature_logpmi.get(name_wrong).keySet().iterator();
			while(it11.hasNext()) {
				int iFeature = it11.next();
				if(id_classifier==1 && !feature1.contains(iFeature))
					continue;
				if(id_classifier==2 && !feature2.contains(iFeature))
					continue;

				weight.put(iFeature, new Double(0));
			}
		}	
		return weight;
	}
	 */
	// return value:
	// HashMap<Integer, Double> weight
	public static HashMap<Integer, Double> setZero_weight_type2(ArrayList<Integer> set_wrong, int id_classifier, HashMap<Integer, Double> centroid,
			HashMap<Integer, Double> weight) {
		Iterator<Integer> it22 = set_wrong.iterator();
		while(it22.hasNext()) {
			int name_wrong = it22.next();
			Iterator<Integer> it11 = ne_feature_logpmi.get(name_wrong).keySet().iterator();
			while(it11.hasNext()) {
				int iFeature = it11.next();
				if(id_classifier==1 && !feature1.contains(iFeature))
					continue;
				if(id_classifier==2 && !feature2.contains(iFeature))
					continue;

				if(centroid.containsKey(iFeature))
					weight.put(iFeature, new Double(0));
			}
		}	
		return weight;
	}

	// return value:
	// HashMap<Integer, Double> weight
	public static HashMap<Integer, Double> FMM_modify_centroid(ArrayList<Integer> set_wrong, int id_classifier, HashMap<Integer, Double> centroid) {
		HashMap<Integer, Double> new_centroid = new HashMap<Integer, Double>(centroid);

		Iterator<Integer> it22 = set_wrong.iterator();
		while(it22.hasNext()) {
			int name_wrong = it22.next();
			Iterator<Integer> it11 = ne_feature_logpmi.get(name_wrong).keySet().iterator();
			while(it11.hasNext()) {
				int iFeature = it11.next();
				if(id_classifier==1 && !feature1.contains(iFeature))
					continue;
				if(id_classifier==2 && !feature2.contains(iFeature))
					continue;

				if(centroid.containsKey(iFeature))
					new_centroid.put(iFeature, 0.0);
			}
		}
		return new_centroid;
	}

	// return value:
	// HashMap<Integer, Double> weight
	public static HashMap<Integer, Double> decrease_weight_type3(ArrayList<Integer> set_wrong, int id_classifier, HashMap<Integer, Double> weight) {
		Iterator<Integer> it22 = set_wrong.iterator();
		while(it22.hasNext()) {
			int name_wrong = it22.next();

			Iterator<Integer> it11 = ne_feature_logpmi.get(name_wrong).keySet().iterator();
			while(it11.hasNext()) {
				int iFeature = it11.next();
				if(id_classifier==1 && !feature1.contains(iFeature))
					continue;
				if(id_classifier==2 && !feature2.contains(iFeature))
					continue;

				if(!weight.containsKey(iFeature)) {
					weight.put(iFeature, new Double(1 * weight_discount));
				}
				else {
					double old_value = weight.get(iFeature);
					weight.put(iFeature, new Double(old_value * weight_discount));
				}
			}
		}

		return weight;
	}

	public static double get_centroid_richicoo_sqrtX2(HashMap<Integer, Double> centroid) {
		double centroid_sqrtX2 = 0;
		double sum = 0;
		Iterator<Integer> it3 = centroid.keySet().iterator();
		while(it3.hasNext()) {
			int iFeature = it3.next();
			double logpmi = centroid.get(iFeature);

			if(Double.isNaN(logpmi))
				continue;

			sum += logpmi * logpmi;

			//String str_test = String.format("iFeature: %d, w: %f, logpmi: %f", iFeature, w, logpmi);
			//System.out.println(str_test);
		}
		centroid_sqrtX2 = Math.sqrt(sum);

		System.out.print("DBG[get_centroid_richicoo_sqrtX2]: centroid.size(): ");
		System.out.print(centroid.size());
		System.out.print(", centroid_sqrtX2: ");
		System.out.println(centroid_sqrtX2);

		return centroid_sqrtX2;
	}

	public static double get_centroid_sqrtX2(HashMap<Integer, Double> centroid, HashMap<Integer, Double> weight) {
		double centroid_sqrtX2 = 0;
		double sum = 0;
		//		int n_of_seeds = seeds.size();
		Iterator<Integer> it3 = centroid.keySet().iterator();
		while(it3.hasNext()) {
			int iFeature = it3.next();
			double logpmi = centroid.get(iFeature);

			double w;
			if(!weight.containsKey(iFeature))
				w = 1;
			else
				w = weight.get(iFeature);

			if(Double.isNaN(logpmi))
				continue;

			sum += w * w * logpmi * logpmi;

			//String str_test = String.format("iFeature: %d, w: %f, logpmi: %f", iFeature, w, logpmi);
			//System.out.println(str_test);
		}
		centroid_sqrtX2 = Math.sqrt(sum);

		System.out.print("DBG: centroid.size(): ");
		System.out.print(centroid.size());
		System.out.print(", centroid_sqrtX2: ");
		System.out.println(centroid_sqrtX2);

		return centroid_sqrtX2;
	}

	public static double get_iNE_sqrtX2(int iNE, int id_classifier, HashMap<Integer, Double> weight) {
		double cur_sqrtX2 = 0;
		Iterator<Integer> it5 = ne_feature_logpmi.get(iNE).keySet().iterator();
		while(it5.hasNext()) {
			int iFeature = it5.next();
			double logpmi = ne_feature_logpmi.get(iNE).get(iFeature);

			if(id_classifier==1 && !feature1.contains(iFeature))
				continue;
			if(id_classifier==2 && !feature2.contains(iFeature))
				continue;

			double w = 1;
			if(!weight.containsKey(iFeature))
				w = 1;
			else
				w = weight.get(iFeature);

			cur_sqrtX2 += w * w * logpmi * logpmi;
		}
		cur_sqrtX2 = Math.sqrt(cur_sqrtX2);

		return cur_sqrtX2;
	}

	// get precision from two views
	public static void get_combine_ne_list(ArrayList<Ne_candidate> vec_name1,
			ArrayList<Ne_candidate> vec_name2) throws IOException {

		String f_out = String.format(file_goldenset + ".pool");
		BufferedWriter out_list = new BufferedWriter(new FileWriter(f_out));

		ArrayList<Ne_candidate> words_sim = new ArrayList<Ne_candidate>();

		// to speed up, use 20000 instead of words_sim.size()
		int max_index = 20000;
		if(vec_name1.size() < max_index)
			max_index = vec_name1.size();
		if(vec_name2.size() < max_index)
			max_index = vec_name2.size();

		for(int cc = 0; cc < max_index; cc++) {
			Ne_candidate f = new Ne_candidate();

			double sim1 = 0, sim2 = 0;
			sim1 = vec_name1.get(cc).sim;

			for(int cc2 = 0; cc2 < max_index; cc2++) {
				if(vec_name2.get(cc2).iNE == f.iNE) {
					sim2 = vec_name2.get(cc2).sim;
					break;
				}
			}

			f.iNE = vec_name1.get(cc).iNE;
			f.sim = 1-(1-sim1)*(1-sim2);

			words_sim.add(f);
		}

		Collections.sort(words_sim, Collections.reverseOrder());

		for(Ne_candidate ne : words_sim) {
			if(ne.sim >= thres_candidate_pool)
				out_list.write(ne.iNE + "\t" + index_ne.get(ne.iNE) + "\t" + ne.sim + "\n");
			else
				break;
		}
		out_list.close();
	}

	// if type = 1, add random corrected into seed
	// if type = 2, infoItem (add informative seed into seed set, penalize weight for incorrect seed)
	// if type = 3, RF (add informative seed into seed set, penalize weight for informative incorrect seed)
	// if type = 4, FMM (pantel. only weight=0 for incorrect seed)
	// if type == 5, bootstrapping, add the most confident item back to seed set
	// if type == 6, add the most confident, and correct item back to seed set
	public static ArrayList<Ne_candidate> sim_round(int round, int id_classifier, 
			int type,
			String out_file) throws IOException {

		String f_out = String.format("%s.%d.%d.%d.sim", out_file, round, type, id_classifier);
		BufferedWriter out_sim = new BufferedWriter(new FileWriter(f_out));

		ArrayList<Ne_candidate> vec_words = new ArrayList<Ne_candidate>();
		HashMap<Integer, Double> weight = new HashMap();

		HashMap<Integer, Double> centroid_richicoo = null;
		if(type3_adjust_type == Type3_adjust_type.Richcco) {
			if(type == 3) {
				centroid_richicoo = get_centroid_richicoo(seeds[type-1], id_classifier, set_wrong[type-1]);

				ArrayList<Weight> vec_weights = new ArrayList<Weight>();

				for(int iFeature : centroid_richicoo.keySet()) {
					Weight we = new Weight();
					we.iFeature = iFeature;
					we.w = centroid_richicoo.get(iFeature);

					vec_weights.add(we);
				}

				Collections.sort(vec_weights, Collections.reverseOrder());
				for(int i_idx = 0; i_idx < vec_weights.size(); i_idx++) {
					String str_test = String.format("%f\t%s\t%d\n", vec_weights.get(i_idx).w, index_feature.get(vec_weights.get(i_idx).iFeature), vec_weights.get(i_idx).iFeature);
					out_sim.write(str_test);
				}
			}
		}
		// calculate centroid
		HashMap<Integer, Double> centroid;
		if(type == 0)
			centroid = get_centroid(seeds[0], id_classifier);
		else
			centroid = get_centroid(seeds[type-1], id_classifier);

		// adjust weight
		if(type3_adjust_type == Type3_adjust_type.Expo) {
			if(type == 3) {
				weight = increase_weight_type3(seeds[type-1], id_classifier, weight);
			}
		}

		// FMM
		if(type==1 || type==2 || type==5) {		
			//			weight = setZero_weight_type2(set_wrong[type-1], id_classifier, weight);
		}
		if(type == 4) {
			//			weight = setZero_weight_type2(set_wrong[type-1], id_classifier, centroid, weight);
			centroid = FMM_modify_centroid(set_wrong[type-1], id_classifier, centroid);
		}

		if(type3_adjust_type == Type3_adjust_type.Expo) {
			// FWM
			if( (type==3)) {
				weight = decrease_weight_type3(set_wrong[type-1], id_classifier, weight);
			}
		}

		/*
		// for debugging, output high weight features
		Iterator<Integer> it_test1 = weight.keySet().iterator();
		while(it_test1.hasNext()) {
			int iFeature = it_test1.next();
			double w = weight.get(iFeature);

			Weight we = new Weight();
			we.iFeature = iFeature;
			we.w = w;

			vec_weights.add(we);
		}
		Collections.sort(vec_weights, Collections.reverseOrder());
		for(int i_idx = 0; i_idx < vec_weights.size(); i_idx++) {
			String str_test = String.format("%f\t%s\t%d\n", vec_weights.get(i_idx).w, index_feature.get(vec_weights.get(i_idx).iFeature), vec_weights.get(i_idx).iFeature);
			out_sim.write(str_test);
		}
		//
		 */

		/*
		if(type==3) {
			Iterator<Integer> it_test1 = weight.keySet().iterator();
			while(it_test1.hasNext()) {
				int iFeature = it_test1.next();
				double w = weight.get(iFeature);
				if(w==0)
					continue;

				System.out.print("*W+: ");
				System.out.print(index_feature.get(iFeature));
				System.out.print(": ");
				System.out.print(weight.get(iFeature));
				System.out.println();
			}

			Iterator<Integer> it_test2 = weight.keySet().iterator();
			while(it_test2.hasNext()) {
				int iFeature = it_test2.next();
				double w = weight.get(iFeature);
				if(w!=0)
					continue;

				System.out.print("*W-: ");
				System.out.print(index_feature.get(iFeature));
				System.out.print(": ");
				System.out.print(weight.get(iFeature));
				System.out.println();
			}
		}
		 */

		/*
		// FWM
		if( (type==3) && name_wrong!=-1) {
			Iterator<Integer> it11 = ne_feature_logpmi.get(name_wrong).keySet().iterator();
			while(it11.hasNext()) {
				int iFeature = it11.next();
				if(id_classifier==1 && !feature1.contains(iFeature))
					continue;
				else if(id_classifier==2 && !feature2.contains(iFeature))
					continue;

				if(!weight.containsKey(iFeature))
					weight.put(iFeature, new Float(0.5));
				else {
					float old_value = weight.get(iFeature);
					weight.put(iFeature, new Float(old_value/2));
				}
			}
		}
		 */		

		// calculate the sqrt of centroid
		double centroid_sqrtX2 = get_centroid_sqrtX2(centroid, weight);
		double centroid_richicoo_sqrtX2 = get_centroid_richicoo_sqrtX2(centroid);

		System.out.println("calculating similarity for all NPs.. ");
		int count = 0;
		int old_perc = -1;

		Iterator<Integer> it4 = ne_feature_logpmi.keySet().iterator();
		while(it4.hasNext()) {
			int iNE = it4.next();

			// type = 0 is for generating candidate pool
			if(type!=0) {
				if(!candidate_pool.contains(iNE))
					continue;
			}

			// progress indicator
			count++;
			int finish_perc = count * 100 / ne_feature_logpmi.keySet().size();
			if(finish_perc % 10 == 0 && finish_perc != old_perc) {
				String str_line = String.format("%d/100 of %d", finish_perc, ne_feature_logpmi.size());
				System.out.println(str_line);
			}

			// skip known correct and incorrect ones
			if(type > 0) {
				if(seeds[type-1].contains(iNE))
					continue;
				if(set_wrong[type-1].contains(iNE))
					continue;
			}

			if(centroid_sqrtX2 != 0) {
				double cur_sqrtX2 = get_iNE_sqrtX2(iNE, id_classifier, weight);
				double sim = 0;

				//				if(type3_adjust_type == Type3_adjust_type.Expo)

				if(type3_adjust_type == Type3_adjust_type.Richcco && type == 3)
					sim = cal_simularity(centroid_richicoo, ne_feature_logpmi.get(iNE), centroid_richicoo_sqrtX2, cur_sqrtX2, id_classifier, weight);
				else
					sim = cal_simularity(centroid, ne_feature_logpmi.get(iNE), centroid_sqrtX2, cur_sqrtX2, id_classifier, weight);

				if(sim >= thres_sim) {
					Ne_candidate f = new Ne_candidate();
					f.iNE = iNE;
					f.sim = sim;
					vec_words.add(f);

					// for testing purpose
					Collections.sort(test_rank_features, Collections.reverseOrder());
					int n_top_features = 10;
					if(test_rank_features.size()<10)
						n_top_features = test_rank_features.size();

					for(int ii=0; ii<n_top_features; ii++) {
						f.top_features.add(test_rank_features.get(ii));
					}

					/*
					if(vec_words.size()<=5) {
						System.out.print("vec_words.size(): ");
						System.out.print(vec_words.size());
						System.out.print(", f.sim: ");
						System.out.println(sim);
					}
					 */
				}
			}
			old_perc = finish_perc;
		}

		System.out.println("finish calculating similarity.");

		//		System.out.print("vec_words.size(): ");
		//		System.out.println(vec_words.size());
		out_sim.close();

		return vec_words;
	}

	/*
	// get the first correct candidate
	// don't add into seed set yet
	public static int get_first_correct(int type, ArrayList<Ne_candidate> vec_name1_1, ArrayList<Ne_candidate> vec_name1_2) {
		int name_correct = -1;

		// pick a new seed: first correct one 
		int max_cc = vec_name1_1.size();
		if(vec_name1_2.size() < max_cc)
			max_cc = vec_name1_2.size();

		for(int i=0; i<max_cc; i++) {
			int name1 = vec_name1_1.get(i).iNE;
			int name2 = vec_name1_2.get(i).iNE;

			boolean is_name1_golden = false;
			boolean is_name2_golden = false;
			Iterator<Integer> it_golden = goldenset.iterator();
			while(it_golden.hasNext()) {
				int iSeed = it_golden.next();
				String str_seed = index_ne.get(iSeed);
				String str_name1 = index_ne.get(name1);
				String str_name2 = index_ne.get(name2);

				if(str_name1.contains(str_seed)) {
					is_name1_golden = true;
					name1 = iSeed;
				}
				if(str_name2.contains(str_seed)) {
					is_name2_golden = true;
					name2 = iSeed;
				}
			}

			if(is_name1_golden &&
					name_correct == -1 &&
					!seeds[type-1].contains(name1) ) {
				name_correct = name1;
				break;
				//				seeds.add(name_correct);
			}

			if(is_name2_golden &&
					name_correct == -1 &&
					!seeds[type-1].contains(name2) ) {
				name_correct = name2;
				break;
				//				seeds.add(name_correct);
			}
		}

		return name_correct;
	}
	 */

	// pick the one with the maximum feature dimension (for feature pruning/modification)
	// from the top 10 correct
	public static int get_pos_maxDim_2view(int type, ArrayList<Ne_candidate> vec_name1_1, ArrayList<Ne_candidate> vec_name1_2) {
		int name_correct = -1;
		int max_feature_dim = 0;

		Set<Integer> top_combine = new HashSet<Integer>();
		Set<Integer> set_pos = new HashSet<Integer>();

		ArrayList<Ne_candidate> list_ranked_by_dimention = new ArrayList<Ne_candidate>();

		// pick ten negative examples
		for(int i=0; i<goldenset.size()*3/4; i++) {
			top_combine.add(vec_name1_1.get(i).iNE);
			top_combine.add(vec_name1_2.get(i).iNE);

			if(is_in_golden_strict(vec_name1_1.get(i).iNE))
				set_pos.add(vec_name1_1.get(i).iNE);				
			if(is_in_golden_strict(vec_name1_2.get(i).iNE))
				set_pos.add(vec_name1_2.get(i).iNE);

			if(set_pos.size() >= 10)
				break;
		}

		for(int name : set_pos) {
			if(!seeds[type-1].contains(name)) {
				if(ne_feature_logpmi.get(name).size() > max_feature_dim) {
					max_feature_dim = ne_feature_logpmi.get(name).size();
					name_correct = name;
				}
			}
		}

		if(type==3)
			System.out.println("NUM_TRAIL\t1\t" + Integer.toString(get_numTrails_firstCorrect(vec_name1_1, vec_name1_2)));
		return name_correct;
	}

	public static int get_numTrails_firstCorrect(ArrayList<Ne_candidate> vec_name1_1, ArrayList<Ne_candidate> vec_name1_2) {		
		ArrayList<Ne_candidate> list_ranked_by_dim = new ArrayList<Ne_candidate>();

		for(int i=0; i<goldenset.size()*3/4; i++) {
			Ne_candidate nec = new Ne_candidate();
			if(is_in_golden_strict(vec_name1_1.get(i).iNE))
				nec.iNE = 1;
			else
				nec.iNE = 0;
			nec.sim = (double) ne_feature_logpmi.get(vec_name1_1.get(i).iNE).size();
			list_ranked_by_dim.add(nec);

			Ne_candidate nec2 = new Ne_candidate();
			if(is_in_golden_strict(vec_name1_2.get(i).iNE))
				nec2.iNE = 1;
			else
				nec2.iNE = 0;
			nec2.sim = (double) ne_feature_logpmi.get(vec_name1_2.get(i).iNE).size();
			list_ranked_by_dim.add(nec2);

			if(list_ranked_by_dim.size() >= 10)
				break;
		}

		Collections.sort(list_ranked_by_dim, Collections.reverseOrder());

		for(int i=0; i<list_ranked_by_dim.size(); i++)
			if(list_ranked_by_dim.get(i).iNE==1)
				return i+1;

		return 10;
	}

	public static int get_numTrails_firstWrong(int type, ArrayList<Ne_candidate> vec_name1_1, ArrayList<Ne_candidate> vec_name1_2) {
		//		Set<Integer> set_neg = new HashSet<Integer>();
		ArrayList<Ne_candidate> list_ranked_by_dim = new ArrayList<Ne_candidate>();

		ArrayList<Integer> top1 = new ArrayList<Integer>();
		ArrayList<Integer> top2 = new ArrayList<Integer>();
		for(int i=0; i<goldenset.size()-seeds[type-1].size(); i++) {
			top1.add(vec_name1_1.get(i).iNE);
			top2.add(vec_name1_2.get(i).iNE);
		}

		for(int i=0; i<goldenset.size()-seeds[type-1].size(); i++) {
			int name1 = top1.get(i);
			int name2 = top2.get(i);

			if(!top2.contains(name1) && !set_wrong[type-1].contains(name1)) {
				Ne_candidate nec = new Ne_candidate();
				if(!is_in_golden_fuzzy(name1))
					nec.iNE = 0;
				else
					nec.iNE = 1;
				nec.sim = (double) ne_feature_logpmi.get(name1).size();
				list_ranked_by_dim.add(nec);
			}

			if(!top1.contains(name2) && !set_wrong[type-1].contains(name2)) {
				Ne_candidate nec = new Ne_candidate();
				if(!is_in_golden_fuzzy(name2))
					nec.iNE = 0;
				else
					nec.iNE = 1;
				nec.sim = (double) ne_feature_logpmi.get(name2).size();
				list_ranked_by_dim.add(nec);
			}

			if(list_ranked_by_dim.size() >= 10)
				break;
		}


		for(int i=0; i<list_ranked_by_dim.size(); i++)
			if(list_ranked_by_dim.get(i).iNE==0)
				return i+1;

		return 10;
	}

	/*
		for(int cc = 0; cc < max_index; cc++) {
			Ne_candidate f = new Ne_candidate();

			double sim1 = 0, sim2 = 0;
			sim1 = vec_name1.get(cc).sim;

			for(int cc2 = 0; cc2 < max_index; cc2++) {
				if(vec_name2.get(cc2).iNE == f.iNE) {
					sim2 = vec_name2.get(cc2).sim;
					break;
				}
			}

			f.iNE = vec_name1.get(cc).iNE;
			f.sim = 1-(1-sim1)*(1-sim2);

			words_sim.add(f);
		}

		Collections.sort(words_sim, Collections.reverseOrder());
	 */


	// pick the one with the maximum feature dimension (for feature pruning/modification)
	// from the top 10 correct
	public static int get_pos_maxDim_singleView(int type, ArrayList<Ne_candidate> vec_name) {
		int name_correct = -1;
		int max_feature_dim = 0;

		Set<Integer> set_pos = new HashSet<Integer>();

		// pick ten negative examples
		for(int i=0; i<goldenset.size()*3/4; i++) {

			if(is_in_golden_fuzzy(vec_name.get(i).iNE))
				set_pos.add(vec_name.get(i).iNE);

			if(set_pos.size() >= 10)
				break;
		}

		for(int name : set_pos) {
			//			if(!seeds[type-1].contains(name)) {
			if(ne_feature_logpmi.get(name).size() > max_feature_dim) {
				max_feature_dim = ne_feature_logpmi.get(name).size();
				name_correct = name;
			}
			//			}
		}

		return name_correct;
	}


	public static int get_most_confident_bootstrapping_singleView(int type, ArrayList<Ne_candidate> vec_name) {
		return vec_name.get(0).iNE;
	}

	public static int get_most_confident_bootstrapping_2view(int type, ArrayList<Ne_candidate> vec_name1, ArrayList<Ne_candidate> vec_name2) {
		ArrayList<Ne_candidate> words_sim = new ArrayList<Ne_candidate>();

		// to speed up, use 20000 instead of words_sim.size()
		int max_index = goldenset.size();
		if(vec_name1.size() < max_index)
			max_index = vec_name1.size();
		if(vec_name2.size() < max_index)
			max_index = vec_name2.size();

		for(int cc = 0; cc < max_index; cc++) {
			Ne_candidate f = new Ne_candidate();

			double sim1 = 0, sim2 = 0;
			sim1 = vec_name1.get(cc).sim;

			for(int cc2 = 0; cc2 < max_index; cc2++) {
				if(vec_name2.get(cc2).iNE == f.iNE) {
					sim2 = vec_name2.get(cc2).sim;
					break;
				}
			}

			f.iNE = vec_name1.get(cc).iNE;
			f.sim = 1-(1-sim1)*(1-sim2);

			words_sim.add(f);
		}

		Collections.sort(words_sim, Collections.reverseOrder());

		return words_sim.get(0).iNE;
	}

	public static int get_first_correct_2view(int type, ArrayList<Ne_candidate> vec_name1, ArrayList<Ne_candidate> vec_name2) {
		ArrayList<Ne_candidate> words_sim = new ArrayList<Ne_candidate>();

		// to speed up, use 20000 instead of words_sim.size()
		int max_index = goldenset.size();
		if(vec_name1.size() < max_index)
			max_index = vec_name1.size();
		if(vec_name2.size() < max_index)
			max_index = vec_name2.size();

		for(int cc = 0; cc < max_index; cc++) {
			Ne_candidate f = new Ne_candidate();

			double sim1 = 0, sim2 = 0;
			sim1 = vec_name1.get(cc).sim;

			for(int cc2 = 0; cc2 < max_index; cc2++) {
				if(vec_name2.get(cc2).iNE == f.iNE) {
					sim2 = vec_name2.get(cc2).sim;
					break;
				}
			}

			f.iNE = vec_name1.get(cc).iNE;
			f.sim = 1-(1-sim1)*(1-sim2);

			words_sim.add(f);
		}

		Collections.sort(words_sim, Collections.reverseOrder());

		for(int i=0; i<words_sim.size(); i++)
			if(is_in_golden_strict(words_sim.get(i).iNE))
				return words_sim.get(i).iNE;
		//		return words_sim.get(0).iNE;
		return -1;
	}

	/*
	public static int get_pos_random(int type, ArrayList<Ne_candidate> vec_name1_1, ArrayList<Ne_candidate> vec_name1_2) {
		int name_correct = -1;

		Set<Integer> top_combine = new HashSet<Integer>();
		Set<Integer> set_pos = new HashSet<Integer>();

		// pick ten negative examples
		for(int i=0; i<goldenset.size()*3/4; i++) {
			top_combine.add(vec_name1_1.get(i).iNE);
			top_combine.add(vec_name1_2.get(i).iNE);

			if(is_in_golden_fuzzy(vec_name1_1.get(i).iNE))
				set_pos.add(vec_name1_1.get(i).iNE);				
			if(is_in_golden_fuzzy(vec_name1_2.get(i).iNE))
				set_pos.add(vec_name1_2.get(i).iNE);

//			if(set_neg.size() > 10)
//				break;
		}

		int steps = generator.nextInt(10000) % set_pos.size();

		int cur_steps = 0;
		for(int name : set_pos) {
			name_correct = name;

			if(cur_steps == steps)
				break;

			cur_steps++;
		}

		return name_correct;
	}
	 */


	public static int get_pos_random_2view(int type, ArrayList<Ne_candidate> vec_name1_1, ArrayList<Ne_candidate> vec_name1_2) {
		int name_correct = -1;

		Set<Integer> top_combine = new HashSet<Integer>();
		Set<Integer> set_pos = new HashSet<Integer>();

		for(int i=0; i<goldenset.size()-seeds[type-1].size(); i++) {
			top_combine.add(vec_name1_1.get(i).iNE);
			top_combine.add(vec_name1_2.get(i).iNE);

			if(is_in_golden_strict(vec_name1_1.get(i).iNE))
				set_pos.add(vec_name1_1.get(i).iNE);				
			if(is_in_golden_strict(vec_name1_2.get(i).iNE))
				set_pos.add(vec_name1_2.get(i).iNE);

			//			if(set_neg.size() > 10)
			//				break;
		}
		//		for(Integer.class)

		int steps = generator.nextInt(10000) % set_pos.size();

		int cur_steps = 0;
		for(int name : set_pos) {
			name_correct = name;

			if(cur_steps == steps)
				break;

			cur_steps++;
		}

		return name_correct;
	}

	public static int get_pos_random_singleView(int type, ArrayList<Ne_candidate> vec_name) {
		int name_correct = -1;

		Set<Integer> set_pos = new HashSet<Integer>();

		for(int i=0; i<goldenset.size()-seeds[type-1].size(); i++) {			
			if(is_in_golden_fuzzy(vec_name.get(i).iNE))
				set_pos.add(vec_name.get(i).iNE);

			//			if(set_pos.size() >= 10)
			//				break;
		}
		//		for(Integer.class)

		int steps = generator.nextInt(10000) % set_pos.size();

		int cur_steps = 0;
		for(int name : set_pos) {
			name_correct = name;

			if(cur_steps == steps)
				break;

			cur_steps++;
		}

		return name_correct;
	}

	/*
	public static int get_first_disagree(int type, ArrayList<Ne_candidate> vec_name1_1, ArrayList<Ne_candidate> vec_name1_2) {
		int name_wrong = -1;

		ArrayList<Integer> top1 = new ArrayList<Integer>();
		ArrayList<Integer> top2 = new ArrayList<Integer>();
		for(int i=0; i<goldenset.size(); i++) {
			top1.add(vec_name1_1.get(i).iNE);
			top2.add(vec_name1_2.get(i).iNE);
		}

		for(int i=0; i<goldenset.size(); i++) {
			int name1 = top1.get(i);
			int name2 = top2.get(i);

			// find name1 and name2 in goldenset
			boolean is_name1_golden = false;
			boolean is_name2_golden = false;
			Iterator<Integer> it_golden = goldenset.iterator();
			while(it_golden.hasNext()) {
				int iSeed = it_golden.next();
				String str_seed = index_ne.get(iSeed);
				String str_name1 = index_ne.get(name1);
				String str_name2 = index_ne.get(name2);

				if(str_name1.contains(str_seed)) {
					is_name1_golden = true;
					name1 = iSeed;
				}
				if(str_name2.contains(str_seed)) {
					is_name2_golden = true;
					name2 = iSeed;
				}
			}

			// pick an erroneous expansion: first disagreed one
			if(!top2.contains(name1)) {
				if(!is_name1_golden &&
						name_wrong == -1 &&
						!set_wrong[type-1].contains(name1) ) {
					name_wrong = name1;
					break;
					//					set_wrong.add(name1);
				}						
			}

			if(!top1.contains(name2)) {
				if(!is_name2_golden &&
						name_wrong == -1 &&
						!set_wrong[type-1].contains(name2) ) {
					name_wrong = name2;
					break;
					//					set_wrong.add(name2);
				}					
			}
		}

		return name_wrong;
	}
	 */

	static boolean is_in_golden_fuzzy(int iNE) {
		boolean is_name_golden = false;

		// fuzy match with golden set
		Iterator<Integer> it_golden = goldenset.iterator();
		while(it_golden.hasNext()) {
			int iSeed = it_golden.next();
			String str_seed = index_ne.get(iSeed);
			String str_name = index_ne.get(iNE);

			LevenshteinDistance LD = new LevenshteinDistance();
			// contains the other
			if(str_name.contains(str_seed) || str_name.equals(str_seed)
					// edit distance <= 2
					|| LD.LD(str_name, str_seed)<=2) {
				is_name_golden = true;
				return true;
			}			
		}

		return is_name_golden;
	}

	static boolean is_in_golden_strict(int iNE) {
		if(goldenset.contains(iNE))
			return true;
		else
			return false;
	}

	public static int get_neg_maxDim_disagree(int type, ArrayList<Ne_candidate> vec_name1_1, ArrayList<Ne_candidate> vec_name1_2) {
		int name_wrong = -1;
		int max_feature_dim = 0;

		Set<Integer> set_neg = new HashSet<Integer>();

		ArrayList<Integer> top1 = new ArrayList<Integer>();
		ArrayList<Integer> top2 = new ArrayList<Integer>();
		for(int i=0; i<goldenset.size()-seeds[type-1].size(); i++) {
			top1.add(vec_name1_1.get(i).iNE);
			top2.add(vec_name1_2.get(i).iNE);
		}

		for(int i=0; i<goldenset.size()-seeds[type-1].size(); i++) {
			int name1 = top1.get(i);
			int name2 = top2.get(i);

			if(!is_in_golden_fuzzy(name1) && !top2.contains(name1) && !set_wrong[type-1].contains(name1))
				set_neg.add(name1);

			if(!is_in_golden_fuzzy(name2) && !top1.contains(name2) && !set_wrong[type-1].contains(name2))
				set_neg.add(name2);

			if(set_neg.size() >= 10)
				break;
		}

		for(int name : set_neg) {
			if(ne_feature_logpmi.get(name).size() > max_feature_dim) {
				max_feature_dim = ne_feature_logpmi.get(name).size();
				name_wrong = name;
			}
		}

		if(type==3)
			System.out.println("NUM_TRAIL\t0\t" + Integer.toString(get_numTrails_firstWrong(type, vec_name1_1, vec_name1_2)));

		return name_wrong;
	}

	// FMM
	public static int get_neg_first_disagree(int type, ArrayList<Ne_candidate> vec_name1_1, ArrayList<Ne_candidate> vec_name1_2) {
		int name_wrong = -1;
		//		int max_feature_dim = 0;

		ArrayList<Integer> set_neg = new ArrayList<Integer>();

		ArrayList<Integer> top1 = new ArrayList<Integer>();
		ArrayList<Integer> top2 = new ArrayList<Integer>();
		for(int i=0; i<goldenset.size()-seeds[type-1].size(); i++) {
			top1.add(vec_name1_1.get(i).iNE);
			top2.add(vec_name1_2.get(i).iNE);
		}

		for(int i=0; i<goldenset.size()-seeds[type-1].size(); i++) {
			int name1 = top1.get(i);
			int name2 = top2.get(i);

			if(!is_in_golden_fuzzy(name1) && !top2.contains(name1) && !set_wrong[type-1].contains(name1))
				set_neg.add(name1);

			if(!is_in_golden_fuzzy(name2) && !top1.contains(name2) && !set_wrong[type-1].contains(name2))
				set_neg.add(name2);

			// only have one or two items
			if(set_neg.size() >= 1)
				break;
		}

		// random pick one
		Collections.shuffle(set_neg);
		for(int name : set_neg) {
			name_wrong = name;
			break;
		}

		if(type==3)
			System.out.println("NUM_TRAIL\t0\t" + Integer.toString(get_numTrails_firstWrong(type, vec_name1_1, vec_name1_2)));

		return name_wrong;
	}

	public static void run_algorithms(String file_output) throws IOException {
		// R-precision
		BufferedWriter out_rp = new BufferedWriter(new FileWriter(file_output + ".rp"));

		int [] name_wrong = new int [num_of_methods];
		int [] name_correct = new int [num_of_methods];

		for(int i=0; i<num_of_methods; i++) {
			name_wrong[i] = -1;
			name_correct[i] = -1;
		}

		//		int name_wrong1 = -1, name_wrong2 = -1, name_wrong3 = -1;
		//		int name_correct1 = -1, name_correct2 = -1, name_correct3 = -1;

		for(int round=1; round <= max_round; round++) {
			String str_f_out_prec = String.format("%s.%d.prec", file_output, round);
			BufferedWriter out_prec = new BufferedWriter(new FileWriter(str_f_out_prec));

			String str_line = String.format("# Round: %d", round);
			System.out.println(str_line);

			for(int i=0; i<num_of_methods; i++) {
				// output seeds sets
				str_line = String.format("# goldenset: %d, seeds[%d]: %d, ", i, goldenset.size(), seeds[i].size());
				out_prec.write(str_line);
				Iterator<Integer> it = seeds[i].iterator();
				while(it.hasNext()) {
					int iSeed = it.next();
					out_prec.write(index_ne.get(iSeed));
					out_prec.write(", ");
				}
				System.out.println();

				// new seed
				out_prec.write("# new seed: ");
				if(name_correct[i] != -1)
					out_prec.write(index_ne.get(name_correct[i]));
				out_prec.write(", error: ");
				if(name_wrong[i] != -1)
					out_prec.write(index_ne.get(name_wrong[i]));
				out_prec.write("\n");
			}

			// load candidate pool
			if(candidate_pool.isEmpty()) {
				String fn_candidate_pool = file_goldenset + ".pool";
				File file_candidates = new File(fn_candidate_pool);
				if(file_candidates.exists()) {
					BufferedReader br1 = new BufferedReader(new FileReader(fn_candidate_pool));
					while ((str_line = br1.readLine()) != null) {
						String [] items = str_line.split("\t");
						candidate_pool.add(Integer.parseInt(items[0]));
					}
					br1.close();
				}
				else {
					ArrayList<Ne_candidate> vec_name1_1 = sim_round(round, 1, 0, file_output);
					ArrayList<Ne_candidate> vec_name1_2 = sim_round(round, 2, 0, file_output);
					get_combine_ne_list(vec_name1_1, vec_name1_2);
				}
			}

			// if type = 1, add random corrected into seed
			// if type = 2, infoItem (add informative seed into seed set)
			// if type = 3, RF (add informative seed into seed set, penalize weight for informative incorrect seed)
			// if type = 4, FMM (pantel. only weight=0 for incorrect seed)
			// if type == 5, bootstrapping, add the most confident item back to seed set
			//			ArrayList<Ne_candidate> vec_name1 = sim_round(round, 3, 1, file_output);
			ArrayList<Ne_candidate> vec_name1_1 = sim_round(round, 1, 1, file_output);
			ArrayList<Ne_candidate> vec_name1_2 = sim_round(round, 2, 1, file_output);

			//			ArrayList<Ne_candidate> vec_name2 = sim_round(round, 3, 2, file_output);
			ArrayList<Ne_candidate> vec_name2_1 = sim_round(round, 1, 2, file_output);
			ArrayList<Ne_candidate> vec_name2_2 = sim_round(round, 2, 2, file_output);

			//			ArrayList<Ne_candidate> vec_name1_1 = sim_round(round, 1, 1, file_output);
			//			ArrayList<Ne_candidate> vec_name1_2 = sim_round(round, 2, 1, file_output);

			//			ArrayList<Ne_candidate> vec_name2_1 = sim_round(round, 1, 2, file_output);
			//			ArrayList<Ne_candidate> vec_name2_2 = sim_round(round, 2, 2, file_output);
			//			System.out.println(vec_name2_2.size());
			//			ArrayList<Ne_candidate> vec_name3 = sim_round(round, 3, 3, file_output);
			ArrayList<Ne_candidate> vec_name3_1 = sim_round(round, 1, 3, file_output);
			ArrayList<Ne_candidate> vec_name3_2 = sim_round(round, 2, 3, file_output);
			//			System.out.println(vec_name3_2.size());
			//			ArrayList<Ne_candidate> vec_name4 = sim_round(round, 3, 4, file_output);
			ArrayList<Ne_candidate> vec_name4_1 = sim_round(round, 1, 4, file_output);
			ArrayList<Ne_candidate> vec_name4_2 = sim_round(round, 2, 4, file_output);

			ArrayList<Ne_candidate> vec_name5_1 = sim_round(round, 1, 5, file_output);
			ArrayList<Ne_candidate> vec_name5_2 = sim_round(round, 2, 5, file_output);

			ArrayList<Ne_candidate> vec_name6_1 = sim_round(round, 1, 6, file_output);
			ArrayList<Ne_candidate> vec_name6_2 = sim_round(round, 2, 6, file_output);

			//			ArrayList<Ne_candidate> vec_name5 = sim_round(round, 3, 5, file_output);
			/*
			if(vec_name1_1.size() < goldenset.size() || vec_name1_2.size() < goldenset.size()
					|| vec_name2.size() < goldenset.size() 
					|| vec_name3.size() < goldenset.size() || vec_name3_1.size() < goldenset.size() || vec_name3_2.size() < goldenset.size() 
					|| vec_name4.size() < goldenset.size() || vec_name4_1.size() < goldenset.size() || vec_name4_2.size() < goldenset.size()
					|| vec_name5.size() < goldenset.size())
				return;
			 */
			if(vec_name1_1.size() < goldenset.size() || vec_name1_2.size() < goldenset.size()
					|| vec_name2_1.size() < goldenset.size() || vec_name2_2.size() < goldenset.size()
					|| vec_name3_1.size() < goldenset.size() || vec_name3_2.size() < goldenset.size() 
					|| vec_name4_1.size() < goldenset.size() || vec_name4_2.size() < goldenset.size()
					|| vec_name5_1.size() < goldenset.size() || vec_name5_2.size() < goldenset.size()
					|| vec_name6_1.size() < goldenset.size() || vec_name6_2.size() < goldenset.size() )
				return;


			//			Collections.sort(vec_name1, Collections.reverseOrder());
			Collections.sort(vec_name1_1, Collections.reverseOrder());
			Collections.sort(vec_name1_2, Collections.reverseOrder());

			//			Collections.sort(vec_name2, Collections.reverseOrder());
			Collections.sort(vec_name2_1, Collections.reverseOrder());
			Collections.sort(vec_name2_2, Collections.reverseOrder());

			//			Collections.sort(vec_name3, Collections.reverseOrder());
			Collections.sort(vec_name3_1, Collections.reverseOrder());
			Collections.sort(vec_name3_2, Collections.reverseOrder());

			//			Collections.sort(vec_name4, Collections.reverseOrder());
			Collections.sort(vec_name4_1, Collections.reverseOrder());
			Collections.sort(vec_name4_2, Collections.reverseOrder());

			//			Collections.sort(vec_name5, Collections.reverseOrder());
			Collections.sort(vec_name5_1, Collections.reverseOrder());
			Collections.sort(vec_name5_2, Collections.reverseOrder());

			Collections.sort(vec_name6_1, Collections.reverseOrder());
			Collections.sort(vec_name6_2, Collections.reverseOrder());

			/*
			Collections.sort(vec_name1_1);
			Collections.sort(vec_name1_2);

			Collections.sort(vec_name2_1);
			Collections.sort(vec_name2_2);

			Collections.sort(vec_name3_1);
			Collections.sort(vec_name3_2);
			 */

			//			HashMap<Integer, Double> map_p1 = get_precision_singleView(vec_name1, file_output, round, 1);		
			HashMap<Integer, Double> map_p1 = get_precision_2view(vec_name1_1, vec_name1_2, file_output, round, 1);
			//			HashMap<Integer, Double> map_p2 = get_precision_singleView(vec_name2, file_output, round, 2);
			HashMap<Integer, Double> map_p2 = get_precision_2view(vec_name2_1, vec_name2_2, file_output, round, 2);
			//			HashMap<Integer, Double> map_p3 = get_precision_singleView(vec_name3, file_output, round, 3);
			HashMap<Integer, Double> map_p3 = get_precision_2view(vec_name3_1, vec_name3_2, file_output, round, 3);
			//			HashMap<Integer, Double> map_p4 = get_precision_singleView(vec_name4, file_output, round, 4);
			HashMap<Integer, Double> map_p4 = get_precision_2view(vec_name4_1, vec_name4_2, file_output, round, 4);
			//			HashMap<Integer, Double> map_p5 = get_precision_singleView(vec_name5, file_output, round, 5);
			HashMap<Integer, Double> map_p5 = get_precision_2view(vec_name5_1, vec_name5_2, file_output, round, 5);

			HashMap<Integer, Double> map_p6 = get_precision_2view(vec_name6_1, vec_name6_2, file_output, round, 6);


			//			HashMap<Integer, Double> map_p1 = get_precision(vec_name1_1, vec_name1_2, file_output, round, 1);
			//			HashMap<Integer, Double> map_p2 = get_precision(vec_name2_1, vec_name2_2, file_output, round, 2);
			//			HashMap<Integer, Double> map_p3 = get_precision(vec_name3_1, vec_name3_2, file_output, round, 3);

			// if type = 1, add random corrected into seed
			// get_pos_random_2view
			//			name_correct[0] = get_pos_random_singleView(1, vec_name1);
			name_correct[0] = get_pos_random_2view(1, vec_name1_1, vec_name1_2);
			seeds[0].add(name_correct[0]);
			// if type = 2, infoItem (add informative seed into seed set)
			name_correct[1] = get_pos_maxDim_2view(2, vec_name2_1, vec_name2_2);
			seeds[1].add(name_correct[1]);
			// if type = 3, RF (add informative seed into seed set, penalize weight for informative incorrect seed)
//			name_correct[2] = get_pos_maxDim_2view(3, vec_name3_1, vec_name3_2);
//			seeds[2].add(name_correct[2]);
//			name_wrong[2] = get_neg_maxDim_disagree(3, vec_name3_1, vec_name3_2);
			name_wrong[2] = get_neg_first_disagree(3, vec_name3_1, vec_name3_2);

			set_wrong[2].add(name_wrong[2]);
			// if type = 4, FMM (pantel. only weight=0 for incorrect seed)
//			name_correct[3] = get_pos_maxDim_2view(4, vec_name4_1, vec_name4_2);
//			seeds[3].add(name_correct[3]);

			/* Bonan : this may cause their performance to be very poor in my ACL submission
			 */
			name_wrong[3] = get_neg_maxDim_disagree(4, vec_name4_1, vec_name4_2);
//			name_wrong[3] = get_neg_first_disagree(4, vec_name4_1, vec_name4_2);
			set_wrong[3].add(name_wrong[3]);
			// if type == 5, bootstrapping, add the most confident item back to seed set
			name_correct[4] = get_most_confident_bootstrapping_2view(5, vec_name5_1, vec_name5_2);
			//			name_correct[4] = get_most_confident_bootstrapping_singleView(5, vec_name5);
			seeds[4].add(name_correct[4]);

			name_correct[5] = get_first_correct_2view(6, vec_name6_1, vec_name6_2);
			//			name_correct[4] = get_most_confident_bootstrapping_singleView(5, vec_name5);
			seeds[5].add(name_correct[5]);
			/*
			name_correct1 = get_pos_random(1, vec_name1_1, vec_name1_2);
			seeds[0].add(name_correct1);
			name_correct2 = get_pos_maxDim(2, vec_name2_1, vec_name2_2);
			seeds[1].add(name_correct2);
			name_correct3 = get_pos_maxDim(3, vec_name3_1, vec_name3_2);
			seeds[2].add(name_correct3);

			name_wrong1 = get_neg_maxDim(1, vec_name1_1, vec_name1_2);
			set_wrong[0].add(name_wrong1);
			name_wrong2 = get_neg_maxDim(2, vec_name2_1, vec_name2_2);
			set_wrong[1].add(name_wrong2);
			name_wrong3 = get_neg_maxDim(3, vec_name3_1, vec_name3_2);
			set_wrong[2].add(name_wrong3);
			 */
			Iterator<Integer> it_map = map_p1.keySet().iterator();
			while(it_map.hasNext()) {
				int rank = it_map.next();
				String line = String.format("%d\t%f\t%f\t%f\t%f\t%f\t%f\n", 
						rank, map_p1.get(rank), map_p2.get(rank), map_p3.get(rank), map_p4.get(rank), map_p5.get(rank), map_p6.get(rank));

				out_prec.write(line);

				if(rank==goldenset.size()) {
					String line_rp = String.format("%d\t%f\t%f\t%f\t%f\t%f\t%f\n", 
							round, map_p1.get(rank), map_p2.get(rank), map_p3.get(rank), map_p4.get(rank), map_p5.get(rank), map_p6.get(rank));
					out_rp.write(line_rp);
				}
			}

			out_prec.close();
		}

		out_rp.close();
	}

	public static void main(String[] arg) throws InterruptedException, IOException {
		String datFilesRoot = "/proteus106/cc3263/datSetExpansion_forChenChen/";
		
		candidate_pool = new HashSet<Integer>();

		test_rank_features = new ArrayList();

		//weight_gain = 0.5;
		//thres_sim = 0.005;

		weight_gain = 1.1;
		weight_discount = 0.9;

		beta = Double.parseDouble(arg[0]);
		gama = Double.parseDouble(arg[1]);		

		thres_sim = Double.parseDouble(arg[2]);

		file_goldenset = arg[3];
		
		// set threshold
		if(file_goldenset.contains("capitals"))
			thres_candidate_pool = 0.015;
		else if(file_goldenset.contains("it_companies"))
			thres_candidate_pool = 0.015;
		else
			thres_candidate_pool = 0.01;
		//
		
		String file_output = arg[4];

		max_seeds = Integer.parseInt(arg[5]);

		generator = new Random();

		System.out.println("# " + file_goldenset);

		load_stop_words(datFilesRoot + "stopwords.dat");
		//		load_stop_words("/scratch/bm1094/setExpansion/stopwords.dat");

		load_goldenset_seeds(file_goldenset);

		if(corpus_type == Corpus_type.NEWS)
			load_index_ne(datFilesRoot + "jetne_index.dat");
		//			load_index_ne("/scratch/bm1094/setExpansion/jetne_index.dat");
		else
			load_index_ne("jetne_index.dat.all.shrinked");

		if(corpus_type == Corpus_type.NEWS) {
			load_idx_feature(datFilesRoot + "feature_index.dat");
			//			load_idx_feature("/scratch/bm1094/setExpansion/feature_index.dat");
			//			load_idx_feature("/data/min/data/proteus1_min_setExpansion/feature_index.syntactToken.dat");
		}
		else
			load_idx_feature("feature_index.dat.all");

		// at the beginning, three seed sets are the same
		String str_line = String.format("%d, ", seeds[0].size());
		System.out.print("# seeds: " + str_line);

		Iterator<Integer> it = seeds[0].iterator();
		while(it.hasNext()) {
			int iSeed = it.next();
			System.out.print(index_ne.get(iSeed) + ", ");
		}
		System.out.println();

		if(corpus_type == Corpus_type.NEWS) {
			load_ne_feature_logpmi(datFilesRoot + "jetne_feature_logpmi.dat");
			//			load_ne_feature_logpmi("/scratch/bm1094/setExpansion/jetne_feature_logpmi.dat");
			//			load_ne_feature_logpmi("/data/min/data/proteus1_min_setExpansion/jetne_feature_logpmi.syntactToken.dat");
		}
		else
			load_ne_feature_logpmi("jetne_feature_logpmi.dat");

		run_algorithms(file_output);
	}
}
