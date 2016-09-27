package sirius.search;

import com.google.common.collect.Lists;
import sirius.search.annotations.IndexMode;
import sirius.search.annotations.Indexed;
import sirius.search.annotations.ListType;

import java.util.List;
import java.util.Map;

@Indexed(index = "test")
public class StringPropertiesEntity extends Entity {

    private String soloStringIncluded;

    @IndexMode(includeInAll = false)
    private String soloStringExcluded;

    @ListType(String.class)
    private List<String> stringListIncluded = Lists.newArrayList();

    @IndexMode(includeInAll = false)
    @ListType(String.class)
    private List<String> stringListExcluded = Lists.newArrayList();

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
}
