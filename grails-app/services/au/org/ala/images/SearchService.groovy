package au.org.ala.images

import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.springframework.web.context.request.RequestContextHolder

import javax.servlet.http.HttpSession
import java.util.regex.Pattern

class SearchService {


    public static final String SEARCH_CRITERIA_SESSION_KEY = "session.key.searchCriteria"

    def simpleSearch(String query, GrailsParameterMap params) {

        query = query.toLowerCase()

        def images = Image.executeQuery("""
            SELECT DISTINCT img FROM Image img LEFT JOIN img.keywords kw
            WHERE (lower(img.originalFilename) like :filenameQuery)
            OR kw.keyword like :keywordQuery
            OR img.imageIdentifier = :query
            ORDER BY img.dateTaken""",
                [query: query, filenameQuery: '%' + query + '%', keywordQuery: query + '%'], [max: params.max, offset: params.offset])

        def totalCount = Image.executeQuery("""
            SELECT COUNT(DISTINCT img) FROM Image img LEFT JOIN img.keywords kw
            WHERE (lower(img.originalFilename) like :filenameQuery)
            OR kw.keyword like :keywordQuery
            OR img.imageIdentifier = :query
            """,
                [query: query, filenameQuery: '%' + query + '%', keywordQuery: query + '%'])[0]

        return [images: images, totalCount: totalCount]
    }

    def findImagesByMetadata(String metaDataKey, List values, GrailsParameterMap params) {

        CodeTimer t = new CodeTimer("Find by metadata key '${metaDataKey}' with ${values?.size()} values")

        if (!metaDataKey || !values) {
            return null
        }

        values = values.collect {
            if (it.contains("*")) {
                return it.replaceAll("\\*", "%").toLowerCase()
            }
            return it
        }

        def c = Image.createCriteria()
        def results = c.list(params) {
            metadata {
                and {
                    ilike("name", metaDataKey)
                    inList('value', values)
                }
            }
        }

        t.stop(true)

        return results
    }

    public void saveSearchCriteria(String id, GrailsParameterMap params) {
        def list = getSearchCriteriaList()
        def existing = list.find { it.id == id }
        if (existing) {
            String value = extractValueForCriteria(existing?.criteriaDefinition, params)
            if (value) {
                existing.value = value
            }
        }
    }

    private String extractValueForCriteria(SearchCriteriaDefinition criteriaDefinition, GrailsParameterMap params) {
        String value = null
        switch (criteriaDefinition.type) {
            case CriteriaType.ImageProperty:

                def extractResults = extractFieldValue(criteriaDefinition, params)

                if (extractResults.errorMessage) {
                    throw new RuntimeException(extractResults.errorMessage)
                } else if (extractResults.value) {
                    value = extractResults.value
                } else {
                    // Should never happen?
                    throw new RuntimeException("No value!")
                }
                break;
            case CriteriaType.ImageMetadata:
                def extractResults = extractFieldValue(criteriaDefinition, params)

                if (extractResults.errorMessage) {
                    throw new RuntimeException(extractResults.errorMessage)
                } else if (extractResults.value) {
                    value = params.metadataItemName + ":" + extractResults.value
                } else {
                    // Should never happen?
                    throw new RuntimeException("No value!")
                }

                break;
            default:
                throw new RuntimeException("Unhandled CriteriaType")
        }
        return value
    }

    public SearchCriteria addSearchCriteria(GrailsParameterMap params) {

        def criteriaDefinition = SearchCriteriaDefinition.get(params.int("searchCriteriaDefinitionId"))

        if (!criteriaDefinition) {
            throw new RuntimeException("Error! A search criteria definition was not selected")
        } else {
            String value = extractValueForCriteria(criteriaDefinition, params)

            if (value) {
                def id = UUID.randomUUID().toString()
                def criteria = new SearchCriteria(id: id, criteriaDefinition: criteriaDefinition, value: value)

                def list = searchCriteriaList
                if (!list) {
                    list = []
                }
                list << criteria
                session.setAttribute(SEARCH_CRITERIA_SESSION_KEY, list)
            }
        }
    }

    public SearchCriteria getSearchCriteria(String id) {
        return searchCriteriaList?.find { it.id == id }
    }

    public List<SearchCriteria> getSearchCriteriaList() {
        def list = session[SEARCH_CRITERIA_SESSION_KEY] as List
        if (!list) {
            list = []
        }
        return list
    }

    public void removeSearchCriteria(String id) {
        def list = searchCriteriaList
        list.removeAll {
            it.id == id
        }
        session.setAttribute(SEARCH_CRITERIA_SESSION_KEY, list)
    }

    public void removeAllSearchCriteria() {
        session.setAttribute(SEARCH_CRITERIA_SESSION_KEY, [])
    }

    private static String joinMulti(Object val) {
        if (val instanceof String) {
            return val as String
        } else {
            return val.join("|")
        }
    }

    private static Map extractFieldValue(SearchCriteriaDefinition criteriaDefinition, GrailsParameterMap params) {

        switch (criteriaDefinition.valueType) {
            case CriteriaValueType.StringMultiSelect:
                if (params.fieldValue) {
                    return [value: joinMulti(params.fieldValue)]
                } else {
                    return [errorMessage: "Please select at least one value for " + criteriaDefinition.name]
                }
                break;
            case CriteriaValueType.StringSingleSelect:
                if (params.fieldValue) {
                    return [value: joinMulti(params.fieldValue)]
                } else {
                    return [errorMessage: "Please select a value for " + criteriaDefinition.name]
                }
                break;
            case CriteriaValueType.StringDirectEntry:
                if (params.fieldValue) {
                    return [value: joinMulti(params.fieldValue)]
                } else {
                    return [errorMessage: "Please enter a value for " + criteriaDefinition.name]
                }
                break;
            case CriteriaValueType.NumberRangeDouble:
                if (params.operator && params.numberValue) {
                    try {
                        def number = Double.parseDouble(params.numberValue)
                        if (params.operator == 'bt') {
                            def number2 = Double.parseDouble(params.numberValue2)
                            return [value:"${params.operator} ${number}:${number2}"]
                        } else {
                            return [value:"${params.operator} ${number}"]
                        }
                    } catch (Exception ex) {
                        return [errorMessage: "Value is not a valid number!"]
                    }
                } else {
                    return [errorMessage: "Please enter a value for " + criteriaDefinition.name]
                }
                break;
            case CriteriaValueType.NumberRangeInteger:
            case CriteriaValueType.NumberRangeLong:
                if (params.operator && params.numberValue) {
                    try {
                        def number = Long.parseLong(params.numberValue)
                        if (params.operator == 'bt') {
                            def number2 = Long.parseLong(params.numberValue2)
                            return [value:"${params.operator} ${number}:${number2}"]
                        } else {
                            return [value:"${params.operator} ${number}"]
                        }
                    } catch (Exception ex) {
                        return [errorMessage: "Value is not a valid integer/long!"]
                    }
                } else {
                    return [errorMessage: "Please enter a value for " + criteriaDefinition.name]
                }
                break
            case CriteriaValueType.DateRange:
                if (params.operator && params.dateValue1) {
                    try {
                        def startDate = Date.parse("dd/MM/yyyy", params.dateValue1 as String)
                        if (params.operator == 'bt') {
                            def endDate = Date.parse("dd/MM/yyyy", params.dateValue2 as String)
                            return [value:"${params.operator} ${params.dateValue1}:${params.dateValue2}"]
                        } else {
                            return [value:"${params.operator} ${params.dateValue1}"]
                        }
                    } catch (Exception ex) {
                        return [errorMessage: "Value is not a valid date!"]
                    }
                } else {
                    return [errorMessage: "Please enter a value for " + criteriaDefinition.name]
                }
                break
            case CriteriaValueType.Boolean:
                if (params.value) {
                    try {
                        boolean val = Boolean.parseBoolean(params.value)
                        return [value: val.toString()]
                    } catch (Exception ex) {
                        return [errorMessage: "Value is not valid - " + params.value]
                    }
                } else {
                    return [errorMessage: "Please enter a value for " + criteriaDefinition.name]
                }
                break;
        }

        return [errorMessage: "Unhandled criteria value type - ${criteriaDefinition.valueType}"]
    }

    def searchUsingCriteria(GrailsParameterMap params) {

        def criteriaList = searchCriteriaList
        def metaDataPattern = Pattern.compile("^(.*)[:](.*)\$")
        // split out by criteria type
        def criteriaMap = criteriaList.groupBy { it.criteriaDefinition.type }
        def c = Image.createCriteria()
        def l = c.list(params) {
            and {
                def list = criteriaMap[CriteriaType.ImageProperty]
                if (list) {
                    SearchCriteriaUtils.buildCriteria(delegate, list)
                }
                list = criteriaMap[CriteriaType.ImageMetadata]
                if (list) {
                    metadata {
                        and {
                            for (int i = 0; i < list.size(); ++i) {
                                def criteria = list[i]
                                // need to split the metadata name out of the value...
                                def matcher = metaDataPattern.matcher(criteria.value)
                                if (matcher.matches()) {
                                    ilike("name", matcher.group(1))
                                    ilike("value", matcher.group(2))
                                }
                            }
                        }
                    }
                }
            }
        }
        return l
    }


    private HttpSession getSession() {
        return RequestContextHolder.currentRequestAttributes().getSession()
    }
}