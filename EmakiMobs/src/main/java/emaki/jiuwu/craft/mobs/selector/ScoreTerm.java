package emaki.jiuwu.craft.mobs.selector;

public sealed interface ScoreTerm permits ScoreTerm.ThreatTerm, ScoreTerm.DistanceTerm,
        ScoreTerm.HealthTerm, ScoreTerm.EquipmentTerm, ScoreTerm.ExpressionTerm {

    double factor();

    record ThreatTerm(double factor) implements ScoreTerm {
    }

    record DistanceTerm(double factor) implements ScoreTerm {
    }

    record HealthTerm(double factor) implements ScoreTerm {
    }

    record EquipmentTerm(String tableId, double factor) implements ScoreTerm {
    }

    record ExpressionTerm(String expressionId, double factor) implements ScoreTerm {
    }
}
