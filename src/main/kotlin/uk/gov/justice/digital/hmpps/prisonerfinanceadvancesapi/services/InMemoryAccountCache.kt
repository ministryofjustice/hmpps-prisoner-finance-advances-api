package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.SubAccountResponse

@Component
class InMemoryAccountCache : SubAccountCache {
  private val store = mutableMapOf<String, SubAccountResponse>()

  override fun put(subAccountRef: String, subAccount: SubAccountResponse) {
    store[subAccountRef] = subAccount
  }

  override fun getOrPut(
    subAccountRef: String,
    supplier: () -> SubAccountResponse,
  ): SubAccountResponse = store.getOrPut(subAccountRef) { supplier() }

  override fun clear() {
    this.store.clear()
  }
}
