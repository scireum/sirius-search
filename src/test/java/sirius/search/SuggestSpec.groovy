/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search

import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.search.suggest.completion.context.CategoryQueryContext
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part
import sirius.search.entities.AutoCompletionPropertyEntity
import sirius.search.suggestion.AutoCompletion

class SuggestSpec extends BaseSpecification {

    @Part
    private static IndexAccess index

    def "test completion suggester with context"() {
        when:
        HashMap<String, List<? extends ToXContent>> qryMap = new HashMap()
        qryMap.put("filter", Arrays.asList(CategoryQueryContext.builder().setCategory("1").build()))

        AutoCompletionPropertyEntity completer = new AutoCompletionPropertyEntity()
        AutoCompletion completerField = new AutoCompletion()
        completerField.setInput(Arrays.asList("Teststring"))
        HashMap<String, List<String>> restriction = new HashMap()
        restriction.put("filter", Arrays.asList("1", "2"))
        completerField.setContext(restriction)
        completer.setComplete(completerField)

        index.create(completer)
        index.blockThreadForUpdate()

        then:

        index.complete(AutoCompletionPropertyEntity.class).on(AutoCompletionPropertyEntity.COMPLETE).prefix("Test").contexts(qryMap)
                .completeList().get(0).getText().toString() == "Teststring"
    }
}
