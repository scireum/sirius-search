/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search

import sirius.kernel.BaseSpecification
import sirius.kernel.commons.Amount
import sirius.kernel.di.std.Part
import sirius.search.entities.*
import sirius.search.properties.ESOption

import java.time.LocalDate
import java.time.LocalDateTime

class PropertiesSpec extends BaseSpecification {

    @Part
    private static IndexAccess index

    def "test AmountProperty"() {
        when:
        def entityNull = new AmountPropertyEntity()
        def entityNothing = new AmountPropertyEntity()
        def entityZero = new AmountPropertyEntity()
        def entityOne = new AmountPropertyEntity()
        def entityMinusOne = new AmountPropertyEntity()
        def entityFortyTwo = new AmountPropertyEntity()
        entityNull.setAmount(null)
        entityNothing.setAmount(Amount.NOTHING)
        entityZero.setAmount(Amount.ZERO)
        entityOne.setAmount(Amount.ONE)
        entityMinusOne.setAmount(Amount.MINUS_ONE)
        entityFortyTwo.setAmount(Amount.of(42.5))
        index.create(entityNull)
        index.create(entityNothing)
        index.create(entityZero)
        index.create(entityOne)
        index.create(entityMinusOne)
        index.create(entityFortyTwo)
        index.blockThreadForUpdate()

        entityNull = index.find(AmountPropertyEntity.class, entityNull.getId())
        entityNothing = index.find(AmountPropertyEntity.class, entityNothing.getId())
        entityZero = index.find(AmountPropertyEntity.class, entityZero.getId())
        entityOne = index.find(AmountPropertyEntity.class, entityOne.getId())
        entityMinusOne = index.find(AmountPropertyEntity.class, entityMinusOne.getId())
        entityFortyTwo = index.find(AmountPropertyEntity.class, entityFortyTwo.getId())

        then:
        entityNull.getAmount() == Amount.NOTHING
        entityNothing.getAmount() == Amount.NOTHING
        entityZero.getAmount() == Amount.ZERO
        entityOne.getAmount() == Amount.ONE
        entityMinusOne.getAmount() == Amount.MINUS_ONE
        entityFortyTwo.getAmount() == Amount.of(42.5)
    }

    def "test BigDecimalProperty"() {
        when:
        def entityNull = new BigDecimalPropertyEntity()
        def entityZero = new BigDecimalPropertyEntity()
        def entityOne = new BigDecimalPropertyEntity()
        def entityMinusOne = new BigDecimalPropertyEntity()
        def entityFortyTwo = new BigDecimalPropertyEntity()
        entityNull.setValue(null)
        entityZero.setValue(BigDecimal.ZERO)
        entityOne.setValue(BigDecimal.ONE)
        entityMinusOne.setValue(BigDecimal.ONE.negate())
        entityFortyTwo.setValue(new BigDecimal(42.5))
        index.create(entityNull)
        index.create(entityZero)
        index.create(entityOne)
        index.create(entityMinusOne)
        index.create(entityFortyTwo)
        index.blockThreadForUpdate()

        entityNull = index.find(BigDecimalPropertyEntity.class, entityNull.getId())
        entityZero = index.find(BigDecimalPropertyEntity.class, entityZero.getId())
        entityOne = index.find(BigDecimalPropertyEntity.class, entityOne.getId())
        entityMinusOne = index.find(BigDecimalPropertyEntity.class, entityMinusOne.getId())
        entityFortyTwo = index.find(BigDecimalPropertyEntity.class, entityFortyTwo.getId())

        then:
        entityNull.getValue() == null
        entityZero.getValue() == BigDecimal.ZERO
        entityOne.getValue() == BigDecimal.ONE
        entityMinusOne.getValue() == BigDecimal.ONE.negate()
        entityFortyTwo.getValue() == new BigDecimal(42.5)
    }

    def "test BooleanProperty"() {
        when:
        def entityNull = new BooleanPropertyEntity()
        def entityTrue = new BooleanPropertyEntity()
        def entityFalse = new BooleanPropertyEntity()
        def entityPrimitiveTrue = new BooleanPropertyEntity()
        def entityPrimitiveFalse = new BooleanPropertyEntity()
        entityNull.setValue(null)
        entityTrue.setValue(Boolean.TRUE)
        entityFalse.setValue(Boolean.FALSE)
        entityPrimitiveTrue.setPrimitiveValue(true)
        entityPrimitiveFalse.setPrimitiveValue(false)
        index.create(entityNull)
        index.create(entityTrue)
        index.create(entityFalse)
        index.create(entityPrimitiveTrue)
        index.create(entityPrimitiveFalse)
        index.blockThreadForUpdate()

        entityNull = index.find(BooleanPropertyEntity.class, entityNull.getId())
        entityTrue = index.find(BooleanPropertyEntity.class, entityTrue.getId())
        entityFalse = index.find(BooleanPropertyEntity.class, entityFalse.getId())
        entityPrimitiveTrue = index.find(BooleanPropertyEntity.class, entityPrimitiveTrue.getId())
        entityPrimitiveFalse = index.find(BooleanPropertyEntity.class, entityPrimitiveFalse.getId())

        then:
        entityNull.getValue() == null
        entityTrue.getValue() == Boolean.TRUE
        entityFalse.getValue() == Boolean.FALSE
        entityPrimitiveTrue.getPrimitiveValue() == true
        entityPrimitiveFalse.getPrimitiveValue() == false
    }

    def "test DoubleProperty"() {
        when:
        def entityNull = new DoublePropertyEntity()
        def entityZero = new DoublePropertyEntity()
        def entityOne = new DoublePropertyEntity()
        def entityMinusOne = new DoublePropertyEntity()
        def entityFortyTwo = new DoublePropertyEntity()
        def entityPrimitiveZero = new DoublePropertyEntity()
        def entityPrimitiveOne = new DoublePropertyEntity()
        def entityPrimitiveMinusOne = new DoublePropertyEntity()
        def entityPrimitiveFortyTwo = new DoublePropertyEntity()
        entityNull.setValue(null)
        entityZero.setValue(new Double(0.0))
        entityOne.setValue(new Double(1.0))
        entityMinusOne.setValue(new Double(-1.0))
        entityFortyTwo.setValue(new Double(42.0))
        entityPrimitiveZero.setPrimitiveValue(0.0)
        entityPrimitiveOne.setPrimitiveValue(1.0)
        entityPrimitiveMinusOne.setPrimitiveValue(-1.0)
        entityPrimitiveFortyTwo.setPrimitiveValue(42.0)
        index.create(entityNull)
        index.create(entityZero)
        index.create(entityOne)
        index.create(entityMinusOne)
        index.create(entityFortyTwo)
        index.create(entityPrimitiveZero)
        index.create(entityPrimitiveOne)
        index.create(entityPrimitiveMinusOne)
        index.create(entityPrimitiveFortyTwo)
        index.blockThreadForUpdate()

        entityNull = index.find(DoublePropertyEntity.class, entityNull.getId())
        entityZero = index.find(DoublePropertyEntity.class, entityZero.getId())
        entityOne = index.find(DoublePropertyEntity.class, entityOne.getId())
        entityMinusOne = index.find(DoublePropertyEntity.class, entityMinusOne.getId())
        entityFortyTwo = index.find(DoublePropertyEntity.class, entityFortyTwo.getId())
        entityPrimitiveZero = index.find(DoublePropertyEntity.class, entityPrimitiveZero.getId())
        entityPrimitiveOne = index.find(DoublePropertyEntity.class, entityPrimitiveOne.getId())
        entityPrimitiveMinusOne = index.find(DoublePropertyEntity.class, entityPrimitiveMinusOne.getId())
        entityPrimitiveFortyTwo = index.find(DoublePropertyEntity.class, entityPrimitiveFortyTwo.getId())

        then:
        entityNull.getValue() == null
        entityZero.getValue() == new Double(0.0)
        entityOne.getValue() == new Double(1.0)
        entityMinusOne.getValue() == new Double(-1.0)
        entityFortyTwo.getValue() == new Double(42.0)
        entityPrimitiveZero.getPrimitiveValue() == 0.0
        entityPrimitiveOne.getPrimitiveValue() == 1.0
        entityPrimitiveMinusOne.getPrimitiveValue() == -1.0
        entityPrimitiveFortyTwo.getPrimitiveValue() == 42.0
    }

    def "test IntProperty"() {
        when:
        def entityNull = new IntPropertyEntity()
        def entityZero = new IntPropertyEntity()
        def entityOne = new IntPropertyEntity()
        def entityMinusOne = new IntPropertyEntity()
        def entityFortyTwo = new IntPropertyEntity()
        def entityPrimitiveZero = new IntPropertyEntity()
        def entityPrimitiveOne = new IntPropertyEntity()
        def entityPrimitiveMinusOne = new IntPropertyEntity()
        def entityPrimitiveFortyTwo = new IntPropertyEntity()
        entityNull.setValue(null)
        entityZero.setValue(new Integer(0))
        entityOne.setValue(new Integer(1))
        entityMinusOne.setValue(new Integer(-1))
        entityFortyTwo.setValue(new Integer(42))
        entityPrimitiveZero.setPrimitiveValue(0)
        entityPrimitiveOne.setPrimitiveValue(1)
        entityPrimitiveMinusOne.setPrimitiveValue(-1)
        entityPrimitiveFortyTwo.setPrimitiveValue(42)
        index.create(entityNull)
        index.create(entityZero)
        index.create(entityOne)
        index.create(entityMinusOne)
        index.create(entityFortyTwo)
        index.create(entityPrimitiveZero)
        index.create(entityPrimitiveOne)
        index.create(entityPrimitiveMinusOne)
        index.create(entityPrimitiveFortyTwo)
        index.blockThreadForUpdate()

        entityNull = index.find(IntPropertyEntity.class, entityNull.getId())
        entityZero = index.find(IntPropertyEntity.class, entityZero.getId())
        entityOne = index.find(IntPropertyEntity.class, entityOne.getId())
        entityMinusOne = index.find(IntPropertyEntity.class, entityMinusOne.getId())
        entityFortyTwo = index.find(IntPropertyEntity.class, entityFortyTwo.getId())
        entityPrimitiveZero = index.find(IntPropertyEntity.class, entityPrimitiveZero.getId())
        entityPrimitiveOne = index.find(IntPropertyEntity.class, entityPrimitiveOne.getId())
        entityPrimitiveMinusOne = index.find(IntPropertyEntity.class, entityPrimitiveMinusOne.getId())
        entityPrimitiveFortyTwo = index.find(IntPropertyEntity.class, entityPrimitiveFortyTwo.getId())

        then:
        entityNull.getValue() == null
        entityZero.getValue() == new Integer(0)
        entityOne.getValue() == new Integer(1)
        entityMinusOne.getValue() == new Integer(-1)
        entityFortyTwo.getValue() == new Integer(42)
        entityPrimitiveZero.getPrimitiveValue() == 0
        entityPrimitiveOne.getPrimitiveValue() == 1
        entityPrimitiveMinusOne.getPrimitiveValue() == -1
        entityPrimitiveFortyTwo.getPrimitiveValue() == 42
    }

    def "test LongProperty"() {
        when:
        def entityNull = new LongPropertyEntity()
        def entityZero = new LongPropertyEntity()
        def entityOne = new LongPropertyEntity()
        def entityMinusOne = new LongPropertyEntity()
        def entityFortyTwo = new LongPropertyEntity()
        def entityPrimitiveZero = new LongPropertyEntity()
        def entityPrimitiveOne = new LongPropertyEntity()
        def entityPrimitiveMinusOne = new LongPropertyEntity()
        def entityPrimitiveFortyTwo = new LongPropertyEntity()
        entityNull.setValue(null)
        entityZero.setValue(new Long(0L))
        entityOne.setValue(new Long(1L))
        entityMinusOne.setValue(new Long(-1L))
        entityFortyTwo.setValue(new Long(42L))
        entityPrimitiveZero.setPrimitiveValue(0L)
        entityPrimitiveOne.setPrimitiveValue(1L)
        entityPrimitiveMinusOne.setPrimitiveValue(-1L)
        entityPrimitiveFortyTwo.setPrimitiveValue(42L)
        index.create(entityNull)
        index.create(entityZero)
        index.create(entityOne)
        index.create(entityMinusOne)
        index.create(entityFortyTwo)
        index.create(entityPrimitiveZero)
        index.create(entityPrimitiveOne)
        index.create(entityPrimitiveMinusOne)
        index.create(entityPrimitiveFortyTwo)
        index.blockThreadForUpdate()

        entityNull = index.find(LongPropertyEntity.class, entityNull.getId())
        entityZero = index.find(LongPropertyEntity.class, entityZero.getId())
        entityOne = index.find(LongPropertyEntity.class, entityOne.getId())
        entityMinusOne = index.find(LongPropertyEntity.class, entityMinusOne.getId())
        entityFortyTwo = index.find(LongPropertyEntity.class, entityFortyTwo.getId())
        entityPrimitiveZero = index.find(LongPropertyEntity.class, entityPrimitiveZero.getId())
        entityPrimitiveOne = index.find(LongPropertyEntity.class, entityPrimitiveOne.getId())
        entityPrimitiveMinusOne = index.find(LongPropertyEntity.class, entityPrimitiveMinusOne.getId())
        entityPrimitiveFortyTwo = index.find(LongPropertyEntity.class, entityPrimitiveFortyTwo.getId())

        then:
        entityNull.getValue() == null
        entityZero.getValue() == new Long(0L)
        entityOne.getValue() == new Long(1L)
        entityMinusOne.getValue() == new Long(-1L)
        entityFortyTwo.getValue() == new Long(42L)
        entityPrimitiveZero.getPrimitiveValue() == 0L
        entityPrimitiveOne.getPrimitiveValue() == 1L
        entityPrimitiveMinusOne.getPrimitiveValue() == -1L
        entityPrimitiveFortyTwo.getPrimitiveValue() == 42L
    }

    def "test LocalDateProperty"() {
        when:
        def entityNull = new LocalDatePropertyEntity()
        def entityVeryPast = new LocalDatePropertyEntity()
        def entityPast = new LocalDatePropertyEntity()
        def entityFuture = new LocalDatePropertyEntity()
        def entityVeryFuture = new LocalDatePropertyEntity()
        entityNull.setValue(null)
        entityVeryPast.setValue(LocalDate.of(-9999, 1, 1))
        entityPast.setValue(LocalDate.of(2017, 8, 3))
        entityFuture.setValue(LocalDate.of(2995, 5, 2))
        entityVeryFuture.setValue(LocalDate.of(9999, 12, 31))
        index.create(entityNull)
        index.create(entityVeryPast)
        index.create(entityPast)
        index.create(entityFuture)
        index.create(entityVeryFuture)
        index.blockThreadForUpdate()

        entityNull = index.find(LocalDatePropertyEntity.class, entityNull.getId())
        entityVeryPast = index.find(LocalDatePropertyEntity.class, entityVeryPast.getId())
        entityPast = index.find(LocalDatePropertyEntity.class, entityPast.getId())
        entityFuture = index.find(LocalDatePropertyEntity.class, entityFuture.getId())
        entityVeryFuture = index.find(LocalDatePropertyEntity.class, entityVeryFuture.getId())

        then:
        entityNull.getValue() == null
        entityVeryPast.getValue() == LocalDate.of(-9999, 1, 1)
        entityPast.getValue() == LocalDate.of(2017, 8, 3)
        entityFuture.getValue() == LocalDate.of(2995, 5, 2)
        entityVeryFuture.getValue() == LocalDate.of(9999, 12, 31)
    }

    def "test LocalDateTimeProperty"() {
        when:
        def entityNull = new LocalDateTimePropertyEntity()
        def entityVeryPast = new LocalDateTimePropertyEntity()
        def entityPast = new LocalDateTimePropertyEntity()
        def entityFuture = new LocalDateTimePropertyEntity()
        def entityVeryFuture = new LocalDateTimePropertyEntity()
        entityNull.setValue(null)
        entityVeryPast.setValue(LocalDateTime.of(-9999, 1, 1, 0, 0, 0))
        entityPast.setValue(LocalDateTime.of(2017, 8, 3, 17, 56, 34))
        entityFuture.setValue(LocalDateTime.of(2995, 5, 2, 2, 58, 12))
        entityVeryFuture.setValue(LocalDateTime.of(9999, 12, 31, 23, 59, 59))
        index.create(entityNull)
        index.create(entityVeryPast)
        index.create(entityPast)
        index.create(entityFuture)
        index.create(entityVeryFuture)
        index.blockThreadForUpdate()

        entityNull = index.find(LocalDateTimePropertyEntity.class, entityNull.getId())
        entityVeryPast = index.find(LocalDateTimePropertyEntity.class, entityVeryPast.getId())
        entityPast = index.find(LocalDateTimePropertyEntity.class, entityPast.getId())
        entityFuture = index.find(LocalDateTimePropertyEntity.class, entityFuture.getId())
        entityVeryFuture = index.find(LocalDateTimePropertyEntity.class, entityVeryFuture.getId())

        then:
        entityNull.getValue() == null
        entityVeryPast.getValue() == LocalDateTime.of(-9999, 1, 1, 0, 0, 0)
        entityPast.getValue() == LocalDateTime.of(2017, 8, 3, 17, 56, 34)
        entityFuture.getValue() == LocalDateTime.of(2995, 5, 2, 2, 58, 12)
        entityVeryFuture.getValue() == LocalDateTime.of(9999, 12, 31, 23, 59, 59)
    }

    def "test EnumProperty"() {
        when:
        def entityNull = new EnumPropertyEntity()
        def entityOne = new EnumPropertyEntity()
        def entityTwo = new EnumPropertyEntity()
        entityNull.setValue(null)
        entityOne.setValue(ESOption.DEFAULT)
        entityTwo.setValue(ESOption.TRUE)
        index.create(entityNull)
        index.create(entityOne)
        index.create(entityTwo)
        index.blockThreadForUpdate()

        entityNull = index.find(EnumPropertyEntity.class, entityNull.getId())
        entityOne = index.find(EnumPropertyEntity.class, entityOne.getId())
        entityTwo = index.find(EnumPropertyEntity.class, entityTwo.getId())

        then:
        entityNull.getValue() == null
        entityOne.getValue() == ESOption.DEFAULT
        entityTwo.getValue() == ESOption.TRUE
    }

    def "test EnumListProperty"() {
        when:
        def entityEmpty = new EnumListPropertyEntity()
        def entityOne = new EnumListPropertyEntity()
        def entityTwo = new EnumListPropertyEntity()
        def entityAll = new EnumListPropertyEntity()
        entityEmpty.getEnumList().clear()
        entityOne.getEnumList().add(ESOption.DEFAULT)
        entityTwo.getEnumList().addAll([ESOption.FALSE, ESOption.TRUE])
        entityAll.getEnumList().addAll(ESOption.values())
        index.create(entityEmpty)
        index.create(entityOne)
        index.create(entityTwo)
        index.create(entityAll)
        index.blockThreadForUpdate()

        entityEmpty = index.find(EnumListPropertyEntity.class, entityEmpty.getId())
        entityOne = index.find(EnumListPropertyEntity.class, entityOne.getId())
        entityTwo = index.find(EnumListPropertyEntity.class, entityTwo.getId())
        entityAll = index.find(EnumListPropertyEntity.class, entityAll.getId())

        then:
        entityEmpty.getEnumList().isEmpty()
        entityOne.getEnumList() == [ESOption.DEFAULT]
        entityTwo.getEnumList() == [ESOption.FALSE, ESOption.TRUE]
        entityAll.getEnumList() == ESOption.values()
    }

    def "test StringProperty and StringListProperty"() {
        when:
        def entityNull = new StringPropertiesEntity()
        def entity = new StringPropertiesEntity()
        entityNull.setSoloString(null)
        entity.setSoloString("soloString")
        entity.getStringList().addAll(["stringListElement1", "stringListElement2"])
        index.create(entityNull)
        index.create(entity)
        index.blockThreadForUpdate()

        entityNull = index.find(StringPropertiesEntity.class, entityNull.getId())
        entity = index.find(StringPropertiesEntity.class, entity.getId())

        then:
        entityNull.getSoloString() == null
        entity.getSoloString() == "soloString"
        entity.getStringList() == ["stringListElement1", "stringListElement2"]
    }

    def "test StringMapProperty"() {
        when:
        def entity = new StringMapPropertyEntity()
        entity.getStringMap().put("SCHLÜSSEL1", "WERT1")
        entity.getStringMap().put("SCHLÜSSEL2", "WERT2")
        index.create(entity)
        index.blockThreadForUpdate()
        then:
        StringMapPropertyEntity result = index.find(StringMapPropertyEntity.class, entity.getId())
        result.getStringMap().size() == 2
        result.getStringMap().get("SCHLÜSSEL1") == "WERT1"
        result.getStringMap().get("SCHLÜSSEL2") == "WERT2"
    }

    def "test ObjectProperty"() {
        when:
        def nested = new POJO()
        nested.setBoolVar(true)
        nested.setStringVar("test")
        nested.setNumberVar(42)
        def entityNull = new NestedObjectEntity()
        def entity = new NestedObjectEntity()
        entityNull.setNestedObject(null)
        entity.setNestedObject(nested)
        index.create(entityNull)
        index.create(entity)
        index.blockThreadForUpdate()

        entityNull = index.find(NestedObjectEntity.class, entityNull.getId())
        entity = index.find(NestedObjectEntity.class, entity.getId())

        then:
        entityNull.getNestedObject() == null
        entity.getNestedObject() != null
        entity.getNestedObject().getBoolVar() == true
        entity.getNestedObject().getNumberVar() == 42
        entity.getNestedObject().getStringVar() == "test"
    }

    def "test ObjectListProperty"() {
        when:
        def nested1 = new POJO()
        nested1.setBoolVar(false)
        nested1.setStringVar("nested1")
        nested1.setNumberVar(42)
        def nested2 = new POJO()
        nested2.setBoolVar(true)
        nested2.setStringVar("nested2")
        nested2.setNumberVar(43)
        def entity = new NestedObjectsListEntity()
        entity.getNestedObjects().addAll([nested1, nested2])
        index.create(entity)
        index.blockThreadForUpdate()
        then:
        NestedObjectsListEntity result = index.find(NestedObjectsListEntity.class, entity.getId())
        result.getNestedObjects().size() == 2
        result.getNestedObjects()[0].getBoolVar() == false
        result.getNestedObjects()[0].getNumberVar() == 42
        result.getNestedObjects()[0].getStringVar() == "nested1"
        result.getNestedObjects()[1].getBoolVar() == true
        result.getNestedObjects()[1].getNumberVar() == 43
        result.getNestedObjects()[1].getStringVar() == "nested2"
    }
}