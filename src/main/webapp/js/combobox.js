Behaviour.specify("INPUT.combobox2", 'combobox-override', 200, function (e) {
    // override autocompletion to search anywhere inside the environment string
    var items = [];

    var c = new ComboBox(e, function (value) {
        var candidates = [];
        for (var i = 0; i < items.length; i++) {
            if (items[i].indexOf(value) >= 0) {
                candidates.push(items[i]);
                if (candidates.length > 20) break;
            }
        }
        return candidates;
    }, {});

    refillOnChange(e, function (params) {
        new Ajax.Request(e.getAttribute("fillUrl"), {
            parameters: params,
            onSuccess: function (rsp) {
                items = eval('(' + rsp.responseText + ')');
            }
        });
    });

    // workaround for missing onchange event issue (https://issues.jenkins-ci.org/browse/JENKINS-13818)
    c.chooseSelection = function () {
        ComboBox.prototype.chooseSelection.call(this)
        fireEvent(this.field, "change");
    }

    // override on mousedown behaviour to show dropdown when clicked
    $(e).on("mousedown", function(e) {
        if (!e) e = window.event;
        c.valueChanged();
        e.cancelBubble = true;
        if (e.stopPropagation) e.stopPropagation();
    });
});