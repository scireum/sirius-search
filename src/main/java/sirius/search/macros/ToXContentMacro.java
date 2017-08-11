/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.macros;

import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.tagliatelle.expression.Expression;
import sirius.tagliatelle.macros.Macro;
import sirius.tagliatelle.rendering.LocalRenderContext;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;

/**
 * Wraps the static methods needed to produce XContent and output as String
 */
@Register
public class ToXContentMacro implements Macro {
    @Override
    public Class<?> getType() {
        return String.class;
    }

    @Override
    public void verifyArguments(List<Expression> args) {
        if ((args.size() != 1) || !(ToXContent.class.isAssignableFrom(args.get(0).getType()))) {
            throw new IllegalArgumentException("One parameter that must implement ToXContent is expected");
        }
    }

    @Override
    public Object eval(LocalRenderContext ctx, Expression[] args) {
        try (XContentBuilder builder = XContentFactory.jsonBuilder().prettyPrint()) {
            return ((ToXContent) args[0].eval(ctx)).toXContent(builder, ToXContent.EMPTY_PARAMS).string();
        } catch (IOException e) {
            Exceptions.handle(e);
        }
        return "";
    }

    @Override
    public boolean isConstant(Expression[] args) {
        return true;
    }

    @Nonnull
    @Override
    public String getName() {
        return "toXContent";
    }

    @Override
    public String getDescription() {
        return "Calls the toXContent method of the given parameter and outputs it as String.";
    }
}
