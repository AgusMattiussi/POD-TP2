package ar.edu.itba.pod.tp2.keypredicates;

import ar.edu.itba.pod.tp2.model.BikeTrip;
import com.hazelcast.mapreduce.KeyPredicate;

@SuppressWarnings("deprecation")
public class SameStationPredicate implements KeyPredicate<BikeTrip> {

    @Override
    public boolean evaluate(BikeTrip bikeTrip) {
        Integer startStationId = bikeTrip.startStationId();
        Integer endStationId = bikeTrip.endStationId();

        return !startStationId.equals(endStationId);
    }
}
