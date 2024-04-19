package killMutations.fromClauseNestedBlock;

import generateConstraints.ConstraintGenerator;
import generateConstraints.GenerateCommonConstraintsForQuery;
import generateConstraints.GenerateConstraintsForConjunct;
import generateConstraints.GenerateConstraintsForHavingClause;
import generateConstraints.GenerateConstraintsForPartialGroup_case2;
import generateConstraints.GenerateGroupByConstraints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import parsing.ConjunctQueryStructure;
import parsing.Node;
import testDataGen.GenerateCVC1;
import testDataGen.QueryBlockDetails;
import util.TagDatasets;

/**
 * Generates data to kill partial group by mutations inside from clause sub query
 * The data sets generated by this function are only capable of killing the mutations even if the group by attribute is not projected in select list of the output
 * Assumptions: Atleast two tuples are needed to satisfy the aggregation constraint. Otherwise it is not possible to generate the data sets
 * @author mahesh
 *
 */
public class PartialGroupByMutationsInFromSubQuery_case2 {

	private static Logger logger = Logger.getLogger(PartialGroupByMutationsInFromSubQuery_case2.class.getName());
	/**
	 * Generates constraints to kill partial group by mutations
	 * @param cvc
	 */
	public static void generateDataForkillingParialGroupByMutationsInFromSubquery(GenerateCVC1 cvc) throws Exception{

		/** keep a copy of this tuple assignment values */
		//HashMap<String, Integer> noOfOutputTuplesOrig = (HashMap<String, Integer>) cvc.getNoOfOutputTuples().clone();
		HashMap<String, Integer> noOfTuplesOrig = (HashMap<String, Integer>) cvc.getNoOfTuples().clone();
		HashMap<String, Integer[]> repeatedRelNextTuplePosOrig = (HashMap<String, Integer[]>)cvc.getRepeatedRelNextTuplePos().clone();

		/** Kill partial group by mutations in each from clause nested block of this query*/
		for(QueryBlockDetails qbt: cvc.getOuterBlock().getFromClauseSubQueries()){

			logger.log(Level.INFO,"\n----------------------------------");
			logger.log(Level.INFO,"GENERATE DATA FOR KILLING PARTIAL GROUP BY MUTATIONS IN FROM CLAUSE NESTED SUBQUERY BLOCK: Case 2: "+qbt);
			logger.log(Level.INFO,"----------------------------------\n");
			
			/**Get group by nodes of this subquery block*/
			ArrayList<Node> groupbyNodes = (ArrayList<Node>)qbt.getGroupByNodes().clone();

			/**kill each group by attribute at a time*/
			for(Node tempgroupByNode : groupbyNodes){

				logger.log(Level.INFO,"\n----------------------------------");
				logger.log(Level.INFO,"KILLING PARTIAL GROUP BY MUTATIONS IN FROM CLAUSE NESTED SUBQUERY BLOCK: " + tempgroupByNode);
				logger.log(Level.INFO,"----------------------------------\n");
				
				/** Initialize the data structures for generating the data to kill this mutation */
				cvc.inititalizeForDatasetQs();

				/**set the type of mutation we are trying to kill*/
				cvc.setTypeOfMutation( TagDatasets.MutationType.PARTIALGROUPBY2, TagDatasets.QueryBlock.FROM_SUBQUERY );


				/** get the tuple assignment for this query
				 * If no possible assignment then not possible to kill this mutation*/
				if(GenerateCVC1.tupleAssignmentForQuery(cvc) == false)					
					continue;


				/** Get constraints for outer query block*/
				/**This also adds constraints for the where clause nested subquery block */
				cvc.getConstraints().add( QueryBlockDetails.getConstraintsForQueryBlockExceptSubQuries(cvc, cvc.getOuterBlock()) );

				/** Add constraints for all the From clause nested subquery blocks except this sub query block */
				for(QueryBlockDetails qb: cvc.getOuterBlock().getFromClauseSubQueries()){
					if(!(qb.equals(qbt))){
						cvc.getConstraints().add(ConstraintGenerator.addCommentLine(" FROM CLAUSE SUBQUERY "));

						cvc.getConstraints().add( QueryBlockDetails.getConstraintsForQueryBlock(cvc, qb) );

						cvc.getConstraints().add(ConstraintGenerator.addCommentLine("END OF FROM CLAUSE SUBQUERY "));
					}
				}


				/** get constraints for this sub query block except group by clause constraints*/
				/** Add the positive conditions for each conjunct of this query block */
				for(ConjunctQueryStructure conjunct : qbt.getConjunctsQs()){
					cvc.getConstraints().add(ConstraintGenerator.addCommentLine(" CONSTRAINTS FOR THIS CONJUNCT "));
					cvc.getConstraints().add( GenerateConstraintsForConjunct.getConstraintsForConjuct(cvc, qbt, conjunct) );
					cvc.getConstraints().add(ConstraintGenerator.addCommentLine("END OF CONSTRAINTS FOR THIS CONJUNCT "));
				}

				/**Add other related constraints for outer query block */
				cvc.getConstraints().add( QueryBlockDetails.getOtherConstraintsForQueryBlock(cvc, qbt) );

				/**FIXME: Make this aggregation constraint to fail in distinct tuples of killing group by attribute*/
				/** Generate havingClause constraints for this sub query block*/
				cvc.getConstraints().add(ConstraintGenerator.addCommentLine("HAVING CLAUSE CONSTRAINTS FOR SUBQUERY BLOCK "));
				for(int j=0; j< qbt.getNoOfGroups();j ++)
					for(int k=0; k < qbt.getAggConstraints().size();k++){
						cvc.getConstraints().add( GenerateConstraintsForHavingClause.getHavingClauseConstraints(cvc, qbt, qbt.getAggConstraints().get(k), qbt.getFinalCount(), j));
					}
				cvc.getConstraints().add(ConstraintGenerator.addCommentLine("END OF HAVING CLAUSE CONSTRAINTS FOR SUBQUERY BLOCK "));



				/** get the equivalence classes in which this group by node is present */
				Vector<Vector<Node>> equivalenceClassGroupBy = new Vector<Vector<Node>>();

				Vector<Vector<Node>> equivalence = new Vector<Vector<Node>>();



				/**It may have involved in joins with other from clause/ outer block*/							
				for(ConjunctQueryStructure con: qbt.getConjunctsQs())
					equivalence.addAll(con.getEquivalenceClasses());

				/**get equivalence classes from other from the outer query block*/
				for(ConjunctQueryStructure con: cvc.getOuterBlock().getConjunctsQs())
					equivalence.addAll(con.getEquivalenceClasses());				


				for(Vector<Node> ec: equivalence)
					if(ec.contains(tempgroupByNode))
						equivalenceClassGroupBy.add(ec);

				/** all group by attributes except this group by node (or the group by nodes which are in the same equivalence class of this node) must be same in the group */
				ArrayList<Node> groupNodes = new ArrayList<Node>();
				for(Node n: groupbyNodes){

					boolean flag = false;
					for(Vector<Node> ec: equivalenceClassGroupBy)/** If this group by node is involved in joins with other group by attribute then they both must contain same value */
						if(ec.contains(n))
							flag = true;
					if(flag)
						continue;					

					if(!n.equals(tempgroupByNode))
						groupNodes.add(n);
				}		

				/** add same group by constraints for this query block */
				cvc.getConstraints().add(ConstraintGenerator.addCommentLine("GROUP BY ATTRIBUTES MUST BE SAME IN SAME GROUP  INSIDE FROM CLAUSE SUBQUERY BLOCK "));
				/**If there are multiple groups then we should ensure that these are distinct across multiple group*/
				cvc.getConstraints().add( GenerateGroupByConstraints.getGroupByConstraints(cvc, groupNodes, true, qbt.getNoOfGroups()) );

				/** add  constraints to kill this mutation */
				cvc.getConstraints().add( ConstraintGenerator.addCommentLine("CONSTRAINTS TO KILL PARTIAL GROUP BY MUTATIONS WITH SINGLE GROUPS  INSIDE FROM CLAUSE SUBQUERY BLOCK "));
				cvc.getConstraints().add( GenerateConstraintsForPartialGroup_case2.getConstraintsForPartialSingleGroup(cvc, qbt, tempgroupByNode) );
				cvc.getConstraints().add( ConstraintGenerator.addCommentLine("END OF CONSTRAINTS TO KILL PARTIAL GROUP BY MUTATIONS WITH SINGLE GROUPS INSIDE FROM CLAUSE SUBQUERY BLOCK "));

				/**FIXME: If any nodes in the equivalence classes of this group by attribute are unique and that table contains only a single tuple then it violates foreignkey relationship
				 * This group by node may involve in joins with outer block or other sub query node, let say 'x'
				 * If 'x' is unique and the number of tuples (or groups in case of other sub query) is '1' then it violates foreign key relationship. (Because we are not aincreasing tuples in foreign key if they are involved in joins)
				 * So we should increase the number of tuples in this case 
				 * So we should add the equivalence classes of outer query block and other from clause sub queries*/

				equivalence = new Vector<Vector<Node>>();
				for(QueryBlockDetails qbb: cvc.getOuterBlock().getFromClauseSubQueries())
					if(!qbb.equals(qbt))
						for(ConjunctQueryStructure cn: qbb.getConjunctsQs())
							equivalence.addAll(cn.getEquivalenceClasses());
				for(ConjunctQueryStructure cn: cvc.getOuterBlock().getConjunctsQs())
					equivalence.addAll(cn.getEquivalenceClasses());

				cvc.getConstraints().add(GenerateConstraintsForPartialGroup_case2.adjustNoOfTuplesForiegnKeyTables(cvc, qbt, tempgroupByNode, equivalence));	

				/** Call the method for the data generation*/
				GenerateCommonConstraintsForQuery.generateDataSetForConstraints(cvc);				
			}
		}

		/** Revert back to the old assignment */
		cvc.setNoOfTuples( (HashMap<String, Integer>) noOfTuplesOrig.clone() );
//		cvc.setNoOfOutputTuples( (HashMap<String, Integer>) noOfOutputTuplesOrig.clone() );
		cvc.setRepeatedRelNextTuplePos( (HashMap<String, Integer[]>)repeatedRelNextTuplePosOrig.clone() );
	}
}