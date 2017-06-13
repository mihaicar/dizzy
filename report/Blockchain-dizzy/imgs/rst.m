task runSellerTransfer(type: JavaExec) {
    classpath = sourceSets.main.runtimeClasspath
    main = 'net.corda.traderdemo.TraderDemoKt'
    args '--role'
    args 'SELLER_TRANSFER'
    args '--amount'
    if (project.hasProperty("amt")){
        args amt
    }
    args '--quantity'
    if (project.hasProperty("qty")){
        args qty
    }
    args '--ticker'
    if (project.hasProperty("tck")){
        args tck
    }
    args '--port'
    if (project.hasProperty("port")) {
        args port
    }
    args '--cparty'
    if (project.hasProperty("cp")) {
        args cp
    }
}