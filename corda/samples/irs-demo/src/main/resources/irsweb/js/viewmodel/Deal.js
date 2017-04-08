'use strict';

define(['viewmodel/FixedLeg', 'viewmodel/FloatingLeg', 'viewmodel/Common'], (fixedLeg, floatingLeg, common) => {
    return {
        fixedLeg: fixedLeg,
        floatingLeg: floatingLeg,
        common: common
    };
});