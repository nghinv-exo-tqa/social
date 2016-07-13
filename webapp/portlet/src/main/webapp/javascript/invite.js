(function($, selectize) {
    var invite = {
        build: function(selector) {
            $('#' + selector).selectize({
                plugins: ['remove_button', 'restore_on_backspace'],
                maxItems: null,
                valueField: 'id',
                labelField: 'title',
                searchField: 'url',
                options: [
                    {id: 1, title: 'Spectrometer', url: 'http://en.wikipedia.org/wiki/Spectrometers'},
                    {id: 2, title: 'Star Chart', url: 'http://en.wikipedia.org/wiki/Star_chart'},
                    {id: 3, title: 'Electrical Tape', url: 'http://en.wikipedia.org/wiki/Electrical_tape'}
                ],
                create: true
            });
        }
    };

    return invite;
})($, selectize);