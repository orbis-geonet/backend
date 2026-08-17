package to.orbis.v2.polygons.creator.utils.points;

import lombok.experimental.UtilityClass;
import to.orbis.v2.polygons.creator.models.dto.PlaceForPalindromeCreationDto;

import java.util.List;

@UtilityClass
public class CircleCalculationPointUtils {

    public void calculateResizedRadius(
            List<PlaceForPalindromeCreationDto> placeDtoList) {
        for (int i = 0; i < placeDtoList.size(); i++) {
            for (int j = i; j < placeDtoList.size(); j++) {
                if (i == j) {
                    continue;
                }
                double distance = PointCommonUtils.calculateDistanceBetweenPoints(
                        placeDtoList.get(i).getCoordinates(), placeDtoList.get(j).getCoordinates());

                boolean isDoResizing =
//                        !placeDtoList.get(i).getDominantGroupKey().equals(placeDtoList.get(j).getDominantGroupKey()) &&
                                distance < placeDtoList.get(i).getSize() + placeDtoList.get(j).getSize();

                if (isDoResizing) {
                    if (placeDtoList.get(i).getTimestamp().equals(placeDtoList.get(j).getTimestamp())) {
                        if (placeDtoList.get(i).getSize() <= placeDtoList.get(j).getSize()) {
                            calculateNewRadius(distance, placeDtoList.get(i), placeDtoList.get(j));
                        } else {
                            calculateNewRadius(distance, placeDtoList.get(j), placeDtoList.get(i));
                        }
                    }
                    if (placeDtoList.get(i).getTimestamp().isBefore(placeDtoList.get(j).getTimestamp())) {
                        calculateNewRadius(distance, placeDtoList.get(i), placeDtoList.get(j));
                    } else {
                        calculateNewRadius(distance, placeDtoList.get(j), placeDtoList.get(i));
                    }
                }
            }
        }

//        for (ExtendedPlaceDto placeDto:placeDtoList) {
//            CircleDto circleDto = CircleDto.builder()
//                    .center(placeDto.getCoordinates())
//                    .radios(placeDto.getSize())
//                    .build();
//            placeDto.setCircleDto(circleDto);
//        }
    }

    private void calculateNewRadius(
            double distance,
            PlaceForPalindromeCreationDto resizedPlace,
            PlaceForPalindromeCreationDto place
    ) {
        if (resizedPlace.getDominantGroupKey() == null) {
            resizedPlace.setSize(0);
        } else if (!resizedPlace.getDominantGroupKey().equals(place.getDominantGroupKey())) {
            double overlap = Math.abs(distance - resizedPlace.getSize() - place.getSize());
            double newRadius = Math.max(0, resizedPlace.getSize() - overlap - 20);
            resizedPlace.setSize(newRadius);
        } else if (place.getSize() > distance + resizedPlace.getSize()) {
            resizedPlace.setSize(0);
        }
    }
}
