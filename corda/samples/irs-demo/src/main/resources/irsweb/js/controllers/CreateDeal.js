'use strict';

define([
    'angular',
    'maskedInput',
    'utils/semantic',
    'utils/dayCountBasisLookup',
    'services/NodeApi',
    'Deal'
], (angular, maskedInput, semantic, dayCountBasisLookup, nodeApi, Deal) => {
    angular.module('irsViewer').controller('CreateDealController', function CreateDealController($http, $scope, $location, nodeService) {
        semantic.init($scope, nodeService.isLoading);

        $scope.dayCountBasisLookup = dayCountBasisLookup;
        $scope.deal = nodeService.newDeal();
        $scope.createDeal = () => {
            nodeService.createDeal(new Deal($scope.deal))
            .then((tradeId) => $location.path('#/deal/' + tradeId), (resp) => {
                $scope.formError = resp.data;
            });
        };
        $('input.percent').mask("9.999999%", {placeholder: "", autoclear: false});
        $('#swapirscolumns').click(() => {
            let first = $('#irscolumns .irscolumn:eq( 0 )');
            let last = $('#irscolumns .irscolumn:eq( 1 )');
            first.before(last);

            let swapPayers = () => {
                let tmp = $scope.deal.floatingLeg.floatingRatePayer;
                $scope.deal.floatingLeg.floatingRatePayer = $scope.deal.fixedLeg.fixedRatePayer;
                $scope.deal.fixedLeg.fixedRatePayer = tmp;
            };
            $scope.$apply(swapPayers);
        });
    });
});