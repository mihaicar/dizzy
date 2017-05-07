"use strict";
const app = angular.module('demoAppModule', ['ui.bootstrap']);

// Fix for unhandled rejections bug.
app.config(['$qProvider', function ($qProvider) {
    $qProvider.errorOnUnhandledRejections(false);
}]);


// demoApp
app.controller('DemoAppController', function($http, $location, $uibModal) {
    const demoApp = this;

    // We identify the node based on its localhost port.
    const nodePort = $location.port();
    const apiBaseURL = "http://localhost:" + nodePort + "/api/example/";
    let peers = [];
    let banks = [];

    $http.get(apiBaseURL + "me").then((response) => demoApp.thisNode = response.data.me);

    $http.get(apiBaseURL + "peers").then((response) => peers = response.data.peers);

    $http.get(apiBaseURL + "banks").then((response) => banks = response.data.banks)

    demoApp.openModal = () => {
        const modalInstance = $uibModal.open({
                templateUrl: 'demoAppModal.html',
                controller: 'ModalInstanceCtrl',
                controllerAs: 'modalInstance',
                resolve: {
                    apiBaseURL: () => apiBaseURL,
                    peers: () => peers,
                    banks: () => banks
                }
    });

        modalInstance.result.then(() => {}, () => {});
    };
    // Gives the map of shares and qty
    demoApp.getPOs = () => $http.get(apiBaseURL + "shares")
        .then((response) => demoApp.pos = response.data);
    demoApp.getPOs();

    demoApp.getCash = () => $http.get(apiBaseURL + "cash-balance")
        .then((response) => demoApp.cash = response.data);
    demoApp.getCash();
});

app.controller('ModalInstanceCtrl', function ($http, $location, $uibModalInstance, $uibModal, apiBaseURL, peers, banks) {
    const modalInstance = this;

    // peers without the bank of corda and the notary!
    modalInstance.peers = peers;
    modalInstance.banks = banks
    modalInstance.form = {};
    modalInstance.formError = false;

    $http.get(apiBaseURL + "me").then((response) => modalInstance.thisNode = response.data.me);

    modalInstance.getPOs = () => $http.get(apiBaseURL + "share-tickers")
        .then((response) => modalInstance.stocks = response.data);
    modalInstance.getPOs();

    // Validate and create purchase order.
    modalInstance.create = () => {
        if (invalidFormInput()) {
            modalInstance.formError = true;
        } else {
            modalInstance.formError = false;

            const po = {
                ticker: modalInstance.form.ticker.toUpperCase(),
                qty: modalInstance.form.qty,
                price: modalInstance.form.price,
            };

            $uibModalInstance.close();

            const sellSharesEndpoint =
                apiBaseURL +
                modalInstance.form.counterparty +
                "/sell-shares";

            // Create PO and handle success / fail responses.
            $http.put(sellSharesEndpoint, angular.toJson(po)).then(
                (result) => modalInstance.displayMessage(result),
                (result) => modalInstance.displayMessage(result)
        );
        }
    };
    modalInstance.createbuy = () => {
        if (invalidFormInput()) {
            modalInstance.formError = true;
        } else {
            modalInstance.formError = false;

            const po = {
                ticker: modalInstance.form.ticker.toUpperCase(),
                qty: modalInstance.form.qty,
                price: modalInstance.form.price,
            };

            $uibModalInstance.close();

            const sellSharesEndpoint =
                apiBaseURL +
                modalInstance.form.counterparty +
                "/sell-fresh-shares";

            // Create PO and handle success / fail responses.
            $http.put(sellSharesEndpoint, angular.toJson(po)).then(
                (result) => modalInstance.displayMessage(result),
                (result) => modalInstance.displayMessage(result)
            );
        }
    };

    modalInstance.getQuote = () => $http.get(apiBaseURL + modalInstance.form.ticker + "/get-quote")
        .then((response) => $("#result").text("One share in " + modalInstance.form.ticker + " sells at: $" + response.data));
    modalInstance.getQuoteBuy = () => $http.get(apiBaseURL + modalInstance.form.ticker + "/get-quote")
        .then((response) => $("#result-buy").text("One share in " + modalInstance.form.ticker + " sells at: $" + response.data));

    modalInstance.displayMessage = (message) => {
        //message is: [object Object]
        console.log("message is: " + message.entity)
        const modalInstanceTwo = $uibModal.open({
            templateUrl: 'messageContent.html',
            controller: 'messageCtrl',
            controllerAs: 'modalInstanceTwo',
            resolve: { message: () => message }
        });

        // No behaviour on close / dismiss.
        modalInstanceTwo.result.then(() => {}, () => {});
    };

    // Close create purchase order modal dialogue.
    modalInstance.cancel = () => $uibModalInstance.dismiss();


    // Validate the purchase order.
    function invalidFormInput() {
        const invalidNonItemFields = !modalInstance.form.ticker
            || isNaN(modalInstance.form.qty)

        const inValidCounterparty = modalInstance.form.counterparty === undefined;


        return invalidNonItemFields || inValidCounterparty;
    }
});

// Controller for success/fail modal dialogue.
app.controller('messageCtrl', function ($uibModalInstance, message) {
    const modalInstanceTwo = this;
    modalInstanceTwo.message = message.data;
});
