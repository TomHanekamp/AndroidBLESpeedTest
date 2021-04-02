package nl.tomhanekamp.blespeedtest.server.model

class Request {
    var firstName: String? = null
    var lastName: String? = null

    constructor(firstName: String?, lastName: String?) {
        this.firstName = firstName
        this.lastName = lastName
    }

    constructor() {}
}