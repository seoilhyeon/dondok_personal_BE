package com.oit.dondok.domain.settlement.entity.converter;

import com.oit.dondok.domain.settlement.entity.SettlementCalculationReason;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class SettlementCalculationReasonConverter
    implements AttributeConverter<SettlementCalculationReason, String> {

  @Override
  public String convertToDatabaseColumn(SettlementCalculationReason attribute) {
    return attribute == null ? null : attribute.toJson();
  }

  @Override
  public SettlementCalculationReason convertToEntityAttribute(String dbData) {
    return dbData == null ? null : SettlementCalculationReason.parse(dbData);
  }
}
