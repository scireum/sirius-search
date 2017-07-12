package sirius.search;

import sirius.search.annotations.IndexMode;
import sirius.search.annotations.Indexed;
import sirius.search.annotations.ListType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static sirius.search.properties.ESOption.FALSE;
import static sirius.search.properties.ESOption.TRUE;

@Indexed(index = "test")
public class StringPropertiesEntity extends Entity {

    @IndexMode(includeInAll = TRUE)
    private String soloStringIncluded;

    @IndexMode(includeInAll = FALSE)
    private String soloStringExcluded;

    @IndexMode(includeInAll = TRUE)
    @ListType(String.class)
    private List<String> stringListIncluded = new ArrayList<>();

    @IndexMode(includeInAll = FALSE)
    @ListType(String.class)
    private List<String> stringListExcluded = new ArrayList<>();

    @ListType(String.class)
    private Map<String, String> stringMap = new HashMap<>();

    public String getSoloStringIncluded() {
        return soloStringIncluded;
    }

    public void setSoloStringIncluded(String soloStringIncluded) {
        this.soloStringIncluded = soloStringIncluded;
    }

    public String getSoloStringExcluded() {
        return soloStringExcluded;
    }

    public void setSoloStringExcluded(String soloStringExcluded) {
        this.soloStringExcluded = soloStringExcluded;
    }

    public List<String> getStringListIncluded() {
        return stringListIncluded;
    }

    public List<String> getStringListExcluded() {
        return stringListExcluded;
    }

    public Map<String, String> getStringMap() {
        return stringMap;
    }
}
