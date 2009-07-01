package playground.mmoyo.PTRouter;

import org.matsim.core.network.LinkImpl;
import org.matsim.core.router.util.TravelTime;

/**
 * A simple ficticious time calculator for a express route search
 * a express Dijstra will be used temporarily not to find optimal path but only to find out if there is a path
 */
public class PTTravelTime1 implements TravelTime {
	
	public PTTravelTime1() {

	}
	
	public double getLinkTravelTime(LinkImpl link, double time) {
		return 1;
	}
}