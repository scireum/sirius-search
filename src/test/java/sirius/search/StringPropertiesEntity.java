package sirius.search;

import sirius.search.annotations.IndexMode;
import sirius.search.annotations.Indexed;
import sirius.search.annotations.ListType;
import sirius.search.annotations.MapType;
import sirius.search.properties.ESOption;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Indexed(index = "test")
public class StringPropertiesEntity extends Entity {

    @IndexMode(includeInAll = ESOption.TRUE)
    private String soloStringIncluded;

    @IndexMode(includeInAll = ESOption.FALSE)
    private String soloStringExcluded;

    @IndexMode(includeInAll = ESOption.TRUE)
    @ListType(String.class)
    private List<String> stringListIncluded = new ArrayList<>();

    @IndexMode(includeInAll = ESOption.FALSE)
    @ListType(String.class)
    private List<String> stringListExcluded = new ArrayList<>();

    @MapType(String.class)
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
