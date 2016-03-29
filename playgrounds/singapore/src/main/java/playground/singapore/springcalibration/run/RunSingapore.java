package playground.singapore.springcalibration.run;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.CharyparNagelActivityScoring;
import org.matsim.core.scoring.functions.CharyparNagelAgentStuckScoring;
import org.matsim.core.scoring.functions.CharyparNagelLegScoring;
import org.matsim.core.scoring.functions.CharyparNagelMoneyScoring;
import org.matsim.core.scoring.functions.CharyparNagelScoringParameters;
import org.matsim.core.scoring.functions.CharyparNagelScoringParametersForPerson;
import org.matsim.core.scoring.functions.SubpopulationCharyparNagelScoringParameters;
import org.matsim.roadpricing.ControlerDefaultsWithRoadPricingModule;
import org.matsim.roadpricing.RoadPricingConfigGroup;

import playground.singapore.scoring.CharyparNagelOpenTimesActivityScoring;


public class RunSingapore {	
	private final static Logger log = Logger.getLogger(RunSingapore.class);
	
	

	public static void main(String[] args) {
		log.info("Running SingaporeControlerRunner");
						
		Config config = ConfigUtils.loadConfig( args[0], new RoadPricingConfigGroup() ) ;
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Controler controler = new Controler(scenario);
		
		controler.setModules(new ControlerDefaultsWithRoadPricingModule());
		
		// scoring function
		controler.setScoringFunctionFactory(new ScoringFunctionFactory() {
			final CharyparNagelScoringParametersForPerson parameters = new SubpopulationCharyparNagelScoringParameters( controler.getScenario() );
			@Inject Network network;
			@Override
			public ScoringFunction createNewScoringFunction(Person person) {
				final CharyparNagelScoringParameters params = parameters.getScoringParameters( person );

				SumScoringFunction sumScoringFunction = new SumScoringFunction();
				sumScoringFunction.addScoringFunction(new CharyparNagelLegScoring(params, network));
				
				// this is the Singaporean scorer with Open times:
				sumScoringFunction.addScoringFunction(new CharyparNagelOpenTimesActivityScoring(params, scenario.getActivityFacilities()));
				//sumScoringFunction.addScoringFunction(new CharyparNagelActivityScoring(params)) ;
				
				sumScoringFunction.addScoringFunction(new CharyparNagelAgentStuckScoring(params));
				sumScoringFunction.addScoringFunction(new CharyparNagelMoneyScoring(params));

				return sumScoringFunction;
			}
		}) ;
		
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addTravelTimeBinding("taxi").to(networkTravelTime());
				addTravelDisutilityFactoryBinding("taxi").to(carTravelDisutilityFactoryKey());
			}
		});
		
			
		controler.addControlerListener(new SingaporeControlerListener());
		
		controler.run();
		log.info("finished SingaporeControlerRunner");
	}
}
