var gulp = require('gulp');
var browserSync = require('browser-sync').create();

// Static server
gulp.task('vast-ima-player', function () {
  browserSync.init({
    watch: true,
    ui: false,
    server: {
      baseDir: "./vast-ima-player"
    }
  });
});
