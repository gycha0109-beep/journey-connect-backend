package com.jc.backend;

import com.jc.backend.post.JourneyPost;
import com.jc.backend.post.Place;
import com.jc.backend.post.PlaceRepository;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import java.util.List;

public final class CanonicalTestData {

    private CanonicalTestData() {}

    public static Region region(RegionRepository regions, String code) {
        return regions.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new AssertionError("Missing seed region: " + code));
    }

    public static JourneyPost publishedPost(
            PlaceRepository places,
            UserAccount author,
            Region region,
            String title,
            String content) {
        Place place = places.save(new Place(region, title + " place"));
        JourneyPost post = new JourneyPost(author, region, title, content);
        post.replacePlaces(List.of(place));
        return post;
    }
}
