package to.orbis.v2.polygons.creator.utils.places;

import to.orbis.v2.polygons.creator.models.dto.PlaceForPalindromeCreationDto;

import java.util.*;
import java.util.stream.Collectors;

public class PlaceIntersectionUtils {
    private final static int MAX_REPEAT = 5;

    public static List<List<PlaceForPalindromeCreationDto>> createExtendedPlacePalindromeDtoMap(
            List<PlaceForPalindromeCreationDto> placeDtoList
    ) {
        Map<String, PlaceForPalindromeCreationDto> extendedPlaceDtoMap = new HashMap<>();
        List<List<String>> listOfLists = new ArrayList<>();

        for (PlaceForPalindromeCreationDto placeDto : placeDtoList) {
            List<String> list = mergeConnectingPlaces(placeDto);
            listOfLists.add(list);
            extendedPlaceDtoMap.put(placeDto.getPlaceKey(), placeDto);
        }

        List<List<String>> mergedPlaces = createIntersectionMap(listOfLists, 0);

        return mergedPlaces.stream()
                .map(places -> places.stream()
                            .map(extendedPlaceDtoMap::get)
                            .collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    private static List<List<String>> createIntersectionMap(
            List<List<String>> input, int count) {
        List<Set<String>> mergedLists = new ArrayList<>();

        // Merge intersecting lists
        for (List<String> list : input) {
            Set<String> currentSet = new HashSet<>(list);
            boolean isMerged = false;
            for (Set<String> set : mergedLists) {
                if (!Collections.disjoint(set, currentSet)) {
                    set.addAll(currentSet);
                    isMerged = true;
                    break;
                }
            }
            if (!isMerged) {
                mergedLists.add(currentSet);
            }
        }

        // Convert sets back to lists for the final result
        List<List<String>> resultList = new ArrayList<>();
        for (Set<String> set : mergedLists) {
            resultList.add(new ArrayList<>(set));
        }

        if (hasCommonStrings(resultList) && count < MAX_REPEAT) {
            return createIntersectionMap(resultList, ++count);
        }

        return resultList;
    }

    private static boolean hasCommonStrings(List<List<String>> listOfLists) {
        for (int i = 0; i < listOfLists.size(); i++) {
            List<String> currentList = listOfLists.get(i);
            for (int j = i + 1; j < listOfLists.size(); j++) {
                List<String> compareList = listOfLists.get(j);
                for (String s : currentList) {
                    if (compareList.contains(s)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<String> mergeConnectingPlaces(PlaceForPalindromeCreationDto place) {
        if (place.getConnectingPlacesByPair().isEmpty()) {
            return List.of(place.getPlaceKey());
        }
        return place.getConnectingPlacesByPair().stream()
                .flatMap(Collection::stream)
                .distinct()
                .toList();
    }
}
