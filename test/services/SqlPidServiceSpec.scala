package services

import helpers.{AppSpec, DatabaseSupport}
import models.PidType

class SqlPidServiceSpec extends AppSpec with DatabaseSupport {

  private def pidService = inject[SqlPidService]

  "SqlPidService" should {
    "find all items of a given type" in {
      await(pidService.findAll(PidType.DOI)).length mustBe 3
    }

    "fetch pids" in {
      val pids = await(pidService.findById(PidType.DOI, "10.14454/fxws-0523"))
      pids mustBe defined
      pids.get.value mustBe "10.14454/fxws-0523"
      pids.get.target mustBe "https://example.com/pid-test-1"
    }

    "fetch pids with tombstones" in {
      val pids = await(pidService.findById(PidType.DOI, "10.14454/fxws-0524"))
      pids mustBe defined
      pids.get.value mustBe "10.14454/fxws-0524"
      pids.get.target mustBe "https://example.com/pid-test-2"
      pids.get.tombstone mustBe defined
      pids.get.tombstone.get.client mustBe "system"
      pids.get.tombstone.get.reason mustBe "Test deletion"
    }

    "create new items" in {
      val newPid = await(pidService.create(PidType.DOI, "10.1234/5678", "https://foo.bar/baz", "system"))
      newPid.target mustBe "https://foo.bar/baz"
      newPid.value mustBe "10.1234/5678"
    }

    "error on creating existing items" in {
      val exception = intercept[Exception] {
        await(pidService.create(PidType.DOI, "10.14454/fxws-0523", "https://foo.bar/baz", "system"))
      }
      exception.getMessage must include("duplicate key value violates unique constraint")
    }

    "update existing items" in {
      val updatedPid = await(pidService.update(PidType.DOI, "10.14454/fxws-0523", "https://foo.bar/baz/updated"))
      updatedPid.target mustBe "https://foo.bar/baz/updated"
      updatedPid.value mustBe "10.14454/fxws-0523"
    }

    "return tombstone info when update existing items" in {
      val updatedPid = await(pidService.update(PidType.DOI, "10.14454/fxws-0524", "https://foo.bar/baz/updated"))
      updatedPid.target mustBe "https://foo.bar/baz/updated"
      updatedPid.value mustBe "10.14454/fxws-0524"
      updatedPid.tombstone mustBe defined
      updatedPid.tombstone.get.client mustBe "system"
    }

    "delete items" in {
      await(pidService.delete(PidType.DOI, "10.14454/fxws-0523")) mustBe true
    }

    "tombstone items" in {
      await(pidService.tombstone(PidType.DOI, "10.14454/fxws-0523", "system", "Test reason")) mustBe true
    }

    "not tombstone non-existing items" in {
      await(pidService.tombstone(PidType.DOI, "10.1234/5678", "system", "Test reason")) mustBe false
    }

    "delete tombstones" in {
      await(pidService.deleteTombstone(PidType.DOI, "10.14454/fxws-0524")) mustBe true
    }
  }
}
