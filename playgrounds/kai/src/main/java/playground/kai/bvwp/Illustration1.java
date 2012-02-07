package playground.kai.bvwp;


public class Illustration1 {
	
	public static void main(String[] args) {

//		useCase1();
		
		useCase2() ;

	}

	private static void useCase1() {
		System.out.println("\n===\nFirst test example, based on nothing.") ;
		
		// create the economic values
		Values economicValues = Illustration1EconomicValues.createEconomicValues1();
		
		// create the base case:
		ScenarioForEval nullfall = Illustration1Scenario.createNullfall1();
		
		// create the policy case:
		ScenarioForEval planfall = Illustration1Scenario.createPlanfall1(nullfall);
		
		{
			// instantiate the class that defines the utility calculation:
			UtilityChanges utilityChanges = new UtilityChangesRuleOfHalf();

			// compute the utilities (currently, results are printed to the console):
			utilityChanges.utilityChange(economicValues, nullfall, planfall) ;
			System.out.println("Without a value of time for freight there are not benefits for rail acceleration measures, despite " +
			"the fact that it is cheaper per km.") ;
		}
		{
			UtilityChanges utilityChanges = new UtilityChangesBVWP2003();
			utilityChanges.utilityChange(economicValues, nullfall, planfall) ;
			System.out.println("As was to be expected, the BVWP'03 benefit for the rail acceleration measure derives " +
					"from the fact that km " +
			"are cheaper by rail than by road.\nIn contrast, there are again no direct time gains.") ;
		}
		{
			UtilityChanges utilityChanges = new UtilityChangesBVWP2015();
			utilityChanges.utilityChange(economicValues, nullfall, planfall) ;
			System.out.println("As it should be, the suggested bvwp'15 method returns the same value as the rule-of-half.") ;
		}
	}

	private static void useCase2() {
		System.out.println("\n===\nentspricht der Rechnung ``Relationsbezogen_mit_generalisierten_Kosten.xlsx'' (PV)") ;

		// create the economic values
		Values economicValues = EconomicValues2.createEconomicValues1();
		
		// create the base case:
		ScenarioForEval nullfall = Scenario2.createNullfall1();
		
		// create the policy case:
		ScenarioForEval planfall = Scenario2.createPlanfall1(nullfall);
		
		{
			// instantiate the class that defines the utility calculation:
			UtilityChanges utilityChanges = new UtilityChangesRuleOfHalf();

			// compute the utilities (currently, results are printed to the console):
			utilityChanges.utilityChange(economicValues, nullfall, planfall) ;
		}
		{
			UtilityChanges utilityChanges = new UtilityChangesBVWP2003();
			utilityChanges.utilityChange(economicValues, nullfall, planfall) ;
			System.out.println("As is somewhat plausible, the bvwp'03 method leads to a smaller utl gain as the rule-of-half.") ;
			System.out.println("One notices the time gains for those persons leaving the road.") ;
			System.out.println("The time LOSSES for those same persons entering the rail are added into the time GAINS of the old users.") ;
			System.out.println("There are also km losses for the persons entering the rail.") ;
			System.out.println("But where are the corresponding km gains for road? Answer: The cost per car km is set to zero.") ;
		}
		{
			UtilityChanges utilityChanges = new UtilityChangesBVWP2015();
			utilityChanges.utilityChange(economicValues, nullfall, planfall) ;
			System.out.println("As it should be, the bvwp'15 method returns the same result as the rule-of-half.") ;
		}
	}

}
