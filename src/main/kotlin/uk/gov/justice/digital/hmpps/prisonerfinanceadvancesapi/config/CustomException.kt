package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config


import org.springframework.http.HttpStatusCode

class CustomException(override val message: String, val status: HttpStatusCode, override val cause: Exception? = null) : Exception(message)