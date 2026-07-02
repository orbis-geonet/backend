package to.orbis.v2.backend.models.requests;

import lombok.Data;

import java.util.List;

@Data
public class CreateUserPicture {
    List<String> pictureUrl;
}
