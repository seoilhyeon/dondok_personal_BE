package com.oit.dondok.domain.settlement.entity.converter;

import com.oit.dondok.domain.settlement.entity.SettlementRuleContextSnapshot;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class SettlementRuleContextSnapshotConverter
    implements AttributeConverter<SettlementRuleContextSnapshot, String> {

  @Override
  public String convertToDatabaseColumn(SettlementRuleContextSnapshot attribute) {
    return attribute == null ? null : attribute.toJson();
  }

  @Override
  public SettlementRuleContextSnapshot convertToEntityAttribute(String dbData) {
    return dbData == null ? null : SettlementRuleContextSnapshot.parse(dbData);
  }
}
