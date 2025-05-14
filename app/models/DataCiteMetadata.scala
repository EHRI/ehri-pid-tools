package models

import play.api.libs.json.{Json, Reads}

/**
 * Scala case classes representing DataCite v4.7 DOI metadata schema
 * Based on: https://schema.datacite.org/meta/kernel-4.7/
 */


/**
 * Main class representing a complete DataCite metadata record.
 *
 * Note: the properties are named to match the DataCite JSON schema.
 */
case class DataCiteMetadata(
  identifiers: Seq[Identifier],
  creators: Seq[Creator],
  titles: Seq[Title],
  publisher: Option[String],
  publicationYear: Option[Int],
  types: ResourceType,
  subjects: Option[Seq[Subject]] = None,
  contributors: Option[Seq[Contributor]] = None,
  dates: Option[Seq[Date]] = None,
  language: Option[String] = None,
  alternateIdentifiers: Option[Seq[AlternateIdentifier]] = None,
  relatedIdentifiers: Option[Seq[RelatedIdentifier]] = None,
  sizes: Option[Seq[String]] = None,
  formats: Option[Seq[String]] = None,
  version: Option[String] = None,
  rightList: Option[Seq[Rights]] = None,
  descriptions: Option[Seq[Description]] = None,
  geoLocations: Option[Seq[GeoLocation]] = None,
  fundingReferences: Option[Seq[FundingReference]] = None,
  relatedItems: Option[Seq[RelatedItem]] = None
) {
  def title: Option[String] = titles.map(_.title).headOption

  def hasVersions: Boolean =
    relatedIdentifiers.toSeq.flatten.exists { relatedIdentifier =>
      relatedIdentifier.relationType == RelationType.HasVersion ||
        relatedIdentifier.relationType == RelationType.IsVersionOf ||
        relatedIdentifier.relationType == RelationType.IsNewVersionOf ||
        relatedIdentifier.relationType == RelationType.IsPreviousVersionOf
    }

  def hasTranslations: Boolean =
    relatedIdentifiers.toSeq.flatten.exists { relatedIdentifier =>
      relatedIdentifier.relationType == RelationType.IsTranslationOf ||
        relatedIdentifier.relationType == RelationType.HasTranslation
    }

  def hasSupplement: Boolean =
    relatedIdentifiers.toSeq.flatten.exists { relatedIdentifier =>
      relatedIdentifier.relationType == RelationType.IsSupplementTo ||
        relatedIdentifier.relationType == RelationType.IsSupplementedBy
    }
}

object DataCiteMetadata {
  private implicit def _identifierReads: Reads[Identifier] = Json.reads[Identifier]
  private implicit def _nameIdentifierReads: Reads[NameIdentifier] = Json.reads[NameIdentifier]
  private implicit def _funderIdentifierReads: Reads[FunderIdentifier] = Json.reads[FunderIdentifier]
  private implicit def _fundingReferenceReads: Reads[FundingReference] = Json.reads[FundingReference]
  private implicit def _affiliationReads: Reads[Affiliation] = Json.reads[Affiliation]
  private implicit def _creatorReads: Reads[Creator] = Json.reads[Creator]
  private implicit def _titleReads: Reads[Title] = Json.reads[Title]
  private implicit def _resourceTypeReads: Reads[ResourceType] = Json.reads[ResourceType]
  private implicit def _subjectReads: Reads[Subject] = Json.reads[Subject]
  private implicit def _contributorReads: Reads[Contributor] = Json.reads[Contributor]
  private implicit def _dateReads: Reads[Date] = Json.reads[Date]
  private implicit def _alternateIdentifierReads: Reads[AlternateIdentifier] = Json.reads[AlternateIdentifier]
  private implicit def _relatedIdentifierReads: Reads[RelatedIdentifier] = Json.reads[RelatedIdentifier]
  private implicit def _rightsReads: Reads[Rights] = Json.reads[Rights]
  private implicit def _descriptionReads: Reads[Description] = Json.reads[Description]
  private implicit def _geoLocationReads: Reads[GeoLocation] = Json.reads[GeoLocation]
  private implicit def _geoLocationPointReads: Reads[GeoLocationPoint] = Json.reads[GeoLocationPoint]
  private implicit def _geoLocationBoxReads: Reads[GeoLocationBox] = Json.reads[GeoLocationBox]
  private implicit def _geoLocationPolygonReads: Reads[GeoLocationPolygon] = Json.reads[GeoLocationPolygon]
  private implicit def _relatedItemReads: Reads[RelatedItem] = Json.reads[RelatedItem]
  private implicit def _relatedItemIdentifierReads: Reads[RelatedItemIdentifier] = Json.reads[RelatedItemIdentifier]
  implicit val _reads: Reads[DataCiteMetadata] = Json.reads[DataCiteMetadata]
}

/**
 * Represents the main DOI identifier
 */
case class Identifier(
  identifier: String,
  identifierType: String = "DOI"
)

/**
 * Represents a creator (author)
 */
case class Creator(
  name: Option[String] = None,
  nameType: Option[NameType.Value] = None,
  givenName: Option[String] = None,
  familyName: Option[String] = None,
  nameIdentifiers: Option[List[NameIdentifier]] = None,
  affiliations: Option[List[Affiliation]] = None
)

/**
 * Represents a title
 */
case class Title(
  title: String,
  titleType: Option[TitleType.Value] = None,
  lang: Option[String] = None
)

/**
 * Represents a resource type
 */
case class ResourceType(
  resourceTypeGeneral: ResourceTypeGeneral.Value,
  resourceType: Option[String]
)

/**
 * Represents a subject
 */
case class Subject(
  subject: String,
  subjectScheme: Option[String] = None,
  schemeURI: Option[String] = None,
  valueURI: Option[String] = None,
  classificationCode: Option[String] = None,
  lang: Option[String] = None
)

/**
 * Represents a contributor
 */
case class Contributor(
  name: String,
  nameType: Option[NameType.Value] = None,
  givenName: Option[String] = None,
  familyName: Option[String] = None,
  nameIdentifiers: Option[List[NameIdentifier]] = None,
  affiliations: Option[List[Affiliation]] = None,
  contributorType: Option[ContributorType.Value] = None
)

/**
 * Represents a date
 */
case class Date(
  date: String, // ISO8601 date format
  dateType: DateType.Value,
  dateInformation: Option[String] = None
)

/**
 * Represents an alternate identifier
 */
case class AlternateIdentifier(
  alternateIdentifier: String,
  alternateIdentifierType: String
)

/**
 * Represents a related identifier
 */
case class RelatedIdentifier(
  relatedIdentifier: String,
  relatedIdentifierType: RelatedIdentifierType.Value,
  relationType: RelationType.Value,
  resourceTypeGeneral: Option[String] = None,
  relatedMetadataScheme: Option[String] = None,
  schemeURI: Option[String] = None,
  schemeType: Option[String] = None
)

/**
 * Represents rights information
 */
case class Rights(
  value: String,
  rightsIdentifier: Option[String] = None,
  rightsIdentifierScheme: Option[String] = None,
  schemeURI: Option[String] = None,
  rightsURI: Option[String] = None,
  lang: Option[String] = None
)

/**
 * Represents a description
 */
case class Description(
  description: String,
  descriptionType: Option[DescriptionType.Value] = None,
  lang: Option[String] = None
)

/**
 * Represents a geographic location
 */
case class GeoLocation(
  geoLocationPoint: Option[GeoLocationPoint] = None,
  geoLocationBox: Option[GeoLocationBox] = None,
  geoLocationPlace: Option[String] = None,
  geoLocationPolygon: Option[List[GeoLocationPolygon]] = None
)

/**
 * Represents a geographical point location
 */
case class GeoLocationPoint(
  pointLongitude: Double,
  pointLatitude: Double
)

/**
 * Represents a geographical box area
 */
case class GeoLocationBox(
  westBoundLongitude: Double,
  eastBoundLongitude: Double,
  southBoundLatitude: Double,
  northBoundLatitude: Double
)

/**
 * Represents a geographical polygon
 */
case class GeoLocationPolygon(
  polygonPoints: List[GeoLocationPoint],
  inPolygonPoint: Option[GeoLocationPoint] = None
)

/**
 * Represents a funding reference
 */
case class FundingReference(
  funderName: String,
  funderIdentifier: Option[FunderIdentifier] = None,
  awardNumber: Option[String] = None,
  awardURI: Option[String] = None,
  awardTitle: Option[String] = None
)

/**
 * Represents a funder identifier
 */
case class FunderIdentifier(
  value: String,
  funderIdentifierType: FunderIdentifierType.Value,
  schemeURI: Option[String] = None
)

/**
 * Represents a related item
 */
case class RelatedItem(
  relatedItemType: ResourceTypeGeneral.Value,
  relationType: RelationType.Value,
  relatedItemIdentifier: Option[RelatedItemIdentifier] = None,
  creators: Option[List[Creator]] = None,
  titles: Option[List[Title]] = None,
  publicationYear: Option[Int] = None,
  volume: Option[String] = None,
  issue: Option[String] = None,
  number: Option[String] = None,
  numberType: Option[NumberType.Value] = None,
  firstPage: Option[String] = None,
  lastPage: Option[String] = None,
  publisher: Option[String] = None,
  edition: Option[String] = None,
  contributors: Option[List[Contributor]] = None
)

/**
 * Represents a related item identifier
 */
case class RelatedItemIdentifier(
  value: String,
  relatedItemIdentifierType: String,
  relatedMetadataScheme: Option[String] = None,
  schemeURI: Option[String] = None,
  schemeType: Option[String] = None
)

/**
 * Represents a name identifier
 */
case class NameIdentifier(
  nameIdentifier: String,
  nameIdentifierScheme: String,
  schemeURI: Option[String] = None
)

/**
 * Represents an affiliation
 */
case class Affiliation(
  name: String,
  affiliationIdentifier: Option[String] = None,
  affiliationIdentifierScheme: Option[String] = None,
  schemeURI: Option[String] = None
)

// Enumerations for controlled vocabularies

/**
 * Name type enumeration
 */
object NameType extends Enumeration with EnumToJSON {
  val Organizational = Value("Organizational")
  val Personal = Value("Personal")
}

/**
 *
 */
object ResourceTypeGeneral extends Enumeration with EnumToJSON {
  val Audiovisual = Value("Audiovisual")
  val Award = Value("Award")
  val Book = Value("Book")
  val BookChapter = Value("BookChapter")
  val Collection = Value("Collection")
  val ComputationalNotebook = Value("ComputationalNotebook")
  val ConferencePaper = Value("ConferencePaper")
  val ConferenceProceeding = Value("ConferenceProceeding")
  val DataPaper = Value("DataPaper")
  val Dataset = Value("Dataset")
  val Dissertation = Value("Dissertation")
  val Event = Value("Event")
  val Image = Value("Image")
  val InteractiveResource = Value("InteractiveResource")
  val Instrument = Value("Instrument")
  val Journal = Value("Journal")
  val JournalArticle = Value("JournalArticle")
  val Model = Value("Model")
  val OutputManagementPlan = Value("OutputManagementPlan")
  val PeerReview = Value("PeerReview")
  val PhysicalObject = Value("PhysicalObject")
  val Preprint = Value("Preprint")
  val Project = Value("Project")
  val Report = Value("Report")
  val Service = Value("Service")
  val Software = Value("Software")
  val Sound = Value("Sound")
  val Standard = Value("Standard")
  val StudyRegistration = Value("StudyRegistration")
  val Text = Value("Text")
  val Workflow = Value("Workflow")
  val Other = Value("Other")
}

/**
 * Title type enumeration
 */
object TitleType extends Enumeration with EnumToJSON {
    val AlternativeTitle = Value("AlternativeTitle")
    val Subtitle = Value("Subtitle")
    val TranslatedTitle = Value("TranslatedTitle")
    val Other = Value("Other")
  }

/**
 * Contributor type enumeration
 */
object ContributorType extends Enumeration with EnumToJSON {
  val ContactPerson = Value("ContactPerson")
  val DataCollector = Value("DataCollector")
  val DataCurator = Value("DataCurator")
  val DataManager = Value("DataManager")
  val Distributor = Value("Distributor")
  val Editor = Value("Editor")
  val HostingInstitution = Value("HostingInstitution")
  val Producer = Value("Producer")
  val ProjectLeader = Value("ProjectLeader")
  val ProjectManager = Value("ProjectManager")
  val ProjectMember = Value("ProjectMember")
  val RegistrationAgency = Value("RegistrationAgency")
  val RegistrationAuthority = Value("RegistrationAuthority")
  val RelatedPerson = Value("RelatedPerson")
  val Researcher = Value("Researcher")
  val ResearchGroup = Value("ResearchGroup")
  val RightsHolder = Value("RightsHolder")
  val Sponsor = Value("Sponsor")
  val Supervisor = Value("Supervisor")
  val WorkPackageLeader = Value("WorkPackageLeader")
  val Other = Value("Other")
}

/**
 * Date type enumeration
 */
object DateType extends Enumeration with EnumToJSON {
  val Accepted = Value("Accepted")
  val Available = Value("Available")
  val Copyrighted = Value("Copyrighted")
  val Collected = Value("Collected")
  val Created = Value("Created")
  val Issued = Value("Issued")
  val Submitted = Value("Submitted")
  val Updated = Value("Updated")
  val Valid = Value("Valid")
  val Withdrawn = Value("Withdrawn")
  val Other = Value("Other")
}

/**
 * Related identifier type enumeration
 */
object RelatedIdentifierType extends Enumeration with EnumToJSON {
  val ARK = Value("ARK")
  val arXiv = Value("arXiv")
  val bibcode = Value("bibcode")
  val DOI = Value("DOI")
  val EAN13 = Value("EAN13")
  val EISSN = Value("EISSN")
  val Handle = Value("Handle")
  val IGSN = Value("IGSN")
  val ISBN = Value("ISBN")
  val ISSN = Value("ISSN")
  val ISTC = Value("ISTC")
  val LISSN = Value("LISSN")
  val LSID = Value("LSID")
  val PMID = Value("PMID")
  val PURL = Value("PURL")
  val RAiD = Value("RAiD")
  val ROR = Value("ROR")
  val RRID = Value("RRID")
  val UPC = Value("UPC")
  val URL = Value("URL")
  val URN = Value("URN")
  val w3id = Value("w3id")
  val Other = Value("Other")
}

object RelationType extends Enumeration with EnumToJSON {
  val IsCitedBy = Value("IsCitedBy")
  val Cites = Value("Cites")
  val IsSupplementTo = Value("IsSupplementTo")
  val IsSupplementedBy = Value("IsSupplementedBy")
  val IsContinuedBy = Value("IsContinuedBy")
  val Continues = Value("Continues")
  val IsDescribedBy = Value("IsDescribedBy")
  val Describes = Value("Describes")
  val HasMetadata = Value("HasMetadata")
  val IsMetadataFor = Value("IsMetadataFor")
  val HasVersion = Value("HasVersion")
  val IsVersionOf = Value("IsVersionOf")
  val IsNewVersionOf = Value("IsNewVersionOf")
  val IsPreviousVersionOf = Value("IsPreviousVersionOf")
  val IsPartOf = Value("IsPartOf")
  val HasPart = Value("HasPart")
  val IsPublishedIn = Value("IsPublishedIn")
  val IsReferencedBy = Value("IsReferencedBy")
  val References = Value("References")
  val IsDocumentedBy = Value("IsDocumentedBy")
  val Documents = Value("Documents")
  val IsCompiledBy = Value("IsCompiledBy")
  val Compiles = Value("Compiles")
  val IsVariantFormOf = Value("IsVariantFormOf")
  val IsOriginalFormOf = Value("IsOriginalFormOf")
  val IsIdenticalTo = Value("IsIdenticalTo")
  val IsReviewedBy = Value("IsReviewedBy")
  val Reviews = Value("Reviews")
  val IsDerivedFrom = Value("IsDerivedFrom")
  val IsSourceOf = Value("IsSourceOf")
  val IsRequiredBy = Value("IsRequiredBy")
  val Requires = Value("Requires")
  val IsObsoletedBy = Value("IsObsoletedBy")
  val Obsoletes = Value("Obsoletes")
  val IsCollectedBy = Value("IsCollectedBy")
  val Collects = Value("Collects")
  val IsTranslationOf = Value("IsTranslationOf")
  val HasTranslation = Value("HasTranslation")
}

/**
 * Description type enumeration
 */
object DescriptionType extends Enumeration with EnumToJSON {
  val Abstract = Value("Abstract")
  val Methods = Value("Methods")
  val SeriesInformation = Value("SeriesInformation")
  val TableOfContents = Value("TableOfContents")
  val TechnicalInfo = Value("TechnicalInfo")
  val Other = Value("Other")
}

/**
 * Funder identifier type enumeration
 */
object FunderIdentifierType extends Enumeration with EnumToJSON {
  val ROR = Value("ROR")
  val GRID = Value("GRID")
  val Crossref = Value("Crossref")
  val DOI = Value("DOI")
  val ISNI = Value("ISNI")
  val Other = Value("Other")
}

/**
 * Number type enumeration
 */
object NumberType extends Enumeration with EnumToJSON {
  val Article = Value("Article")
  val Chapter = Value("Chapter")
  val Edition = Value("Edition")
  val Episode = Value("Episode")
  val Issue = Value("Issue")
  val Other = Value("Other")
  val Part = Value("Part")
  val Report = Value("Report")
  val Volume = Value("Volume")
}