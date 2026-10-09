package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services

import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.SubAccountResponse

interface SubAccountCache {
  fun put(subAccountRef: String, subAccount: SubAccountResponse)
  fun getOrPut(
    subAccountRef: String,
    supplier: () -> SubAccountResponse,
  ): SubAccountResponse
  fun clear()
}
