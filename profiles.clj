{:workstation
 {:env {:use-ssl false
        :google-client-id "652200734300-o2vc5k00257n974vpe7m5njches98b0s.apps.googleusercontent.com"
        :google-client-secret "GpztgEE-4NsTwAwrefoKSAnG"
        :google-callback-domain "http://localhost:3000"
        :database-url "postgres://verbcoach:verbcoach@localhost:5432/verbcoach"}}
 
 :production
 {:env {:use-ssl true}}
  
 :travis-ci
 {:env {:database-url "postgres://postgres@localhost:5432/verbcoach"
        :postgres-env "travis-ci"}}}




