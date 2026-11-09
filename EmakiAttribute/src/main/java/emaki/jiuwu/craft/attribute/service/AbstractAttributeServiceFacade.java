package emaki.jiuwu.craft.attribute.service;

import emaki.jiuwu.craft.corelib.api.text.Texts;

abstract class AbstractAttributeServiceFacade implements AttributeServiceFacade {

    protected String normalizeId(String value) {
        return Texts.normalizeId(value);
    }
}
