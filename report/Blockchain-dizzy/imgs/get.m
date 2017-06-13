@GET
@Path("me")
@Produces(MediaType.APPLICATION_JSON)
fun whoami() = mapOf("me" to myLegalName)