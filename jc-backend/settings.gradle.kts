rootProject.name = "jc-backend"

include(":jc-recommendation-core")
project(":jc-recommendation-core").projectDir = file("../jc-recommendation-core")

include(":jc-intelligence-contracts")
project(":jc-intelligence-contracts").projectDir = file("../jc-intelligence-contracts")

include(":jc-search-contracts")
project(":jc-search-contracts").projectDir = file("../jc-search-contracts")

include(":jc-search-compatibility")
project(":jc-search-compatibility").projectDir = file("../jc-search-compatibility")

include(":jc-search-runtime")
project(":jc-search-runtime").projectDir = file("../jc-search-runtime")

include(":jc-search-integration")
project(":jc-search-integration").projectDir = file("../jc-search-integration")
include(":jc-search-shadow-wiring")
project(":jc-search-shadow-wiring").projectDir = file("../jc-search-shadow-wiring")
include(":jc-search-readiness")
project(":jc-search-readiness").projectDir = file("../jc-search-readiness")

include(":jc-search-production-controls")
project(":jc-search-production-controls").projectDir = file("../jc-search-production-controls")
